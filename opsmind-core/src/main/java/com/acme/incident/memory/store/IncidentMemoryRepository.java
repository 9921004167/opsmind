package com.acme.incident.memory.store;

import com.acme.incident.memory.config.IncidentMemoryProperties;
import com.acme.incident.memory.retrieval.HistoricalIncidentMatch;
import java.sql.Array;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.sql.Connection;

/**
 * Plain JdbcTemplate. JPA and pgvector can be made to cooperate, but the
 * queries that matter here are ANN scans with session GUCs, which is exactly
 * the territory where an ORM buys nothing.
 */
@Repository
public class IncidentMemoryRepository {

    private final JdbcTemplate jdbc;
    private final IncidentMemoryProperties props;

    public IncidentMemoryRepository(JdbcTemplate jdbc, IncidentMemoryProperties props) {
        this.jdbc = jdbc;
        this.props = props;
    }

    // ------------------------------------------------------------------
    // Ingest
    // ------------------------------------------------------------------

    /**
     * Insert or refresh a memory row and mark it for embedding.
     *
     * If the content hash is unchanged we leave the row (and its vector)
     * alone: incidents get edited after resolution and re-embedding a typo
     * fix is pure token burn.
     */
    public void upsertPending(UUID incidentId,
                              UUID organizationId,
                              String content,
                              String contentHash,
                              List<String> serviceNames,
                              String severity,
                              Instant resolvedAt,
                              String model) {

        jdbc.update(connection -> {
    var ps = connection.prepareStatement("""
            INSERT INTO incident_memory (
                id, organization_id, incident_id, content, content_hash,
                embedding_model, service_names, severity, resolved_at,
                status, attempts, next_attempt_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, now())
            ON CONFLICT (incident_id) DO UPDATE SET
                content         = EXCLUDED.content,
                content_hash    = EXCLUDED.content_hash,
                embedding_model = EXCLUDED.embedding_model,
                service_names   = EXCLUDED.service_names,
                severity        = EXCLUDED.severity,
                resolved_at     = EXCLUDED.resolved_at,
                status          = 'PENDING',
                attempts        = 0,
                last_error      = NULL,
                next_attempt_at = now(),
                embedding       = NULL,
                updated_at      = now()
            WHERE incident_memory.content_hash <> EXCLUDED.content_hash
               OR incident_memory.embedding_model <> EXCLUDED.embedding_model
               OR incident_memory.status = 'FAILED'
            """);

    ps.setObject(1, UUID.randomUUID());
    ps.setObject(2, organizationId);
    ps.setObject(3, incidentId);
    ps.setString(4, content);
    ps.setString(5, contentHash);
    ps.setString(6, model);

    Array sqlArray = connection.createArrayOf(
            "text",
            serviceNames == null ? new String[0] : serviceNames.toArray(new String[0])
    );
    ps.setArray(7, sqlArray);

    ps.setString(8, severity);
    ps.setTimestamp(9, Timestamp.from(resolvedAt));

    return ps;
});
    }

    /**
     * Claim a batch for this worker. SKIP LOCKED means several instances can
     * run the scheduler without stepping on each other.
     */
    public List<PendingMemory> claimBatch(String model) {
        return jdbc.query("""
                UPDATE incident_memory m
                   SET status = 'PROCESSING', updated_at = now()
                 WHERE m.id IN (
                        SELECT id FROM incident_memory
                         WHERE status IN ('PENDING', 'FAILED')
                           AND attempts < ?
                           AND next_attempt_at <= now()
                           AND embedding_model = ?
                         ORDER BY next_attempt_at
                         LIMIT ?
                         FOR UPDATE SKIP LOCKED)
                RETURNING m.id, m.organization_id, m.incident_id, m.content, m.attempts
                """,
                (rs, i) -> new PendingMemory(
                        rs.getObject("id", UUID.class),
                        rs.getObject("organization_id", UUID.class),
                        rs.getObject("incident_id", UUID.class),
                        rs.getString("content"),
                        rs.getInt("attempts")),
                props.worker().maxAttempts(), model, props.worker().batchSize());
    }

    public void markReady(UUID id, float[] embedding) {
        jdbc.update("""
                UPDATE incident_memory
                   SET embedding = ?::vector,
                       status = 'READY',
                       attempts = attempts + 1,
                       last_error = NULL,
                       updated_at = now()
                 WHERE id = ?
                """, VectorLiteral.of(embedding), id);
    }

    public void markFailed(UUID id, String error, Duration backoff) {
        jdbc.update("""
                UPDATE incident_memory
                   SET status = 'FAILED',
                       attempts = attempts + 1,
                       last_error = ?,
                       next_attempt_at = now() + (? || ' seconds')::interval,
                       updated_at = now()
                 WHERE id = ?
                """, truncateError(error), backoff.toSeconds(), id);
    }

    /**
     * Return rows abandoned in PROCESSING (instance died mid-call) to the
     * queue. Without this they are invisible to claimBatch forever.
     */
    public int reclaimStale(Duration olderThan) {
        return jdbc.update("""
                UPDATE incident_memory
                   SET status = 'PENDING', updated_at = now()
                 WHERE status = 'PROCESSING'
                   AND updated_at < now() - (? || ' seconds')::interval
                """, olderThan.toSeconds());
    }

    /** Rows that exhausted their retries. Surface these on a dashboard. */
    public int countDeadLettered() {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM incident_memory WHERE status = 'FAILED' AND attempts >= ?",
                Integer.class, props.worker().maxAttempts());
        return n == null ? 0 : n;
    }

    // ------------------------------------------------------------------
    // Retrieval
    // ------------------------------------------------------------------

    /**
     * Tenant-scoped ANN search.
     *
     * organization_id is not optional and not a caller convenience: the HNSW
     * index has no tenant column in it, so Postgres walks the graph globally
     * and applies this filter afterwards. Without iterative scans that
     * silently costs recall (you ask for 10, you get 3, no error). Hence the
     * two SET LOCALs below plus over-fetching.
     *
     * Must run inside a transaction for SET LOCAL to apply.
     */
    public List<HistoricalIncidentMatch> searchSimilar(UUID organizationId,
                                                       float[] queryVector,
                                                       UUID excludeIncidentId,
                                                       String model,
                                                       int limit) {

        jdbc.execute("SET LOCAL hnsw.iterative_scan = 'relaxed_order'");
        jdbc.execute("SET LOCAL hnsw.ef_search = " + props.retrieval().efSearch());

        String vec = VectorLiteral.of(queryVector);
        Instant cutoff = Instant.now().minus(props.retrieval().maxAge());

        return jdbc.query("""
                SELECT m.incident_id,
                       m.organization_id,
                       m.content,
                       m.service_names,
                       m.severity,
                       m.resolved_at,
                       1 - (m.embedding <=> ?::vector) AS similarity
                  FROM incident_memory m
                 WHERE m.organization_id = ?
                   AND m.status = 'READY'
                   AND m.embedding_model = ?
                   AND m.resolved_at >= ?
                   AND (?::uuid IS NULL OR m.incident_id <> ?::uuid)
                 ORDER BY m.embedding <=> ?::vector
                 LIMIT ?
                """,
                MATCH_MAPPER,
                vec, organizationId, model, Timestamp.from(cutoff),
                excludeIncidentId, excludeIncidentId, vec, limit);
    }

    private static final RowMapper<HistoricalIncidentMatch> MATCH_MAPPER = (rs, i) -> {
        Array arr = rs.getArray("service_names");
        List<String> services = arr == null
                ? List.of()
                : Arrays.asList((String[]) arr.getArray());
        return new HistoricalIncidentMatch(
                rs.getObject("incident_id", UUID.class),
                rs.getObject("organization_id", UUID.class),
                rs.getString("content"),
                services,
                rs.getString("severity"),
                rs.getTimestamp("resolved_at").toInstant(),
                rs.getDouble("similarity"),
                rs.getDouble("similarity"));  // final score filled in by the reranker
    };

    // ------------------------------------------------------------------
    // Feedback
    // ------------------------------------------------------------------
    
    @org.springframework.transaction.annotation.Transactional(
            propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void recordRetrieval(UUID organizationId,
                                UUID queryIncidentId,
                                List<HistoricalIncidentMatch> matches) {
        for (int rank = 0; rank < matches.size(); rank++) {
            HistoricalIncidentMatch m = matches.get(rank);
            jdbc.update("""
                    INSERT INTO incident_memory_feedback (
                        id, organization_id, query_incident_id, retrieved_incident_id,
                        rank, similarity, final_score, used_in_rca)
                    VALUES (?, ?, ?, ?, ?, ?, ?, TRUE)
                    ON CONFLICT (query_incident_id, retrieved_incident_id) DO UPDATE SET
                        rank = EXCLUDED.rank,
                        similarity = EXCLUDED.similarity,
                        final_score = EXCLUDED.final_score
                    """,
                    UUID.randomUUID(), organizationId, queryIncidentId, m.incidentId(),
                    rank + 1, m.similarity(), m.finalScore());
        }
    }

    public void markHelpful(UUID queryIncidentId, UUID retrievedIncidentId, boolean helpful) {
        jdbc.update("""
                UPDATE incident_memory_feedback SET was_helpful = ?
                 WHERE query_incident_id = ? AND retrieved_incident_id = ?
                """, helpful, queryIncidentId, retrievedIncidentId);
    }

    // ------------------------------------------------------------------


    private static String truncateError(String e) {
        if (e == null) {
            return null;
        }
        return e.length() <= 2000 ? e : e.substring(0, 2000);
    }
}
