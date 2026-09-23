package com.acme.incident.memory.retrieval;

import com.acme.incident.memory.config.IncidentMemoryProperties;
import com.acme.incident.memory.document.IncidentMemoryDocumentBuilder;
import com.acme.incident.memory.embedding.EmbeddingTask;
import com.acme.incident.memory.embedding.GeminiEmbeddingClient;
import com.acme.incident.memory.port.ActiveIncidentView;
import com.acme.incident.memory.store.IncidentMemoryRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Top-K retrieval of similar resolved incidents, scoped to one organization.
 *
 * Pipeline:
 *   1. Build the query document (same shape as stored documents).
 *   2. Embed with the QUERY task instruction.
 *   3. ANN search, over-fetching K * candidateMultiplier.
 *   4. Rerank: cosine + service overlap + recency.
 *   5. Threshold, then cut to K.
 *
 * Step 5 matters more than it looks. Vector search always returns its
 * nearest neighbours, however far away they are. Hand an unrelated incident
 * to the RCA model and it will dutifully explain how the two are connected.
 */
@Service
public class IncidentMemoryRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(IncidentMemoryRetrievalService.class);

    private final IncidentMemoryRepository repository;
    private final IncidentMemoryDocumentBuilder builder;
    private final GeminiEmbeddingClient embeddingClient;
    private final IncidentMemoryProperties props;

    public IncidentMemoryRetrievalService(IncidentMemoryRepository repository,
                                          IncidentMemoryDocumentBuilder builder,
                                          GeminiEmbeddingClient embeddingClient,
                                          IncidentMemoryProperties props) {
        this.repository = repository;
        this.builder = builder;
        this.embeddingClient = embeddingClient;
        this.props = props;
    }

    @Transactional(readOnly = true)
    public List<HistoricalIncidentMatch> findSimilar(ActiveIncidentView active) {
        Objects.requireNonNull(active.organizationId(), "organizationId is required for retrieval");

        IncidentMemoryProperties.Retrieval cfg = props.retrieval();
        String queryText = builder.buildQuery(active);

        float[] queryVector;
        try {
            queryVector = embeddingClient.embed(queryText, EmbeddingTask.QUERY);
        } catch (Exception e) {
            // Memory is an enhancement, never a dependency: a failed lookup
            // must not block the Phase 6 investigation.
            log.warn("query embedding failed for incident {}; continuing without memory",
                    active.incidentId(), e);
            return List.of();
        }

        List<HistoricalIncidentMatch> candidates = repository.searchSimilar(
                active.organizationId(),
                queryVector,
                active.incidentId(),
                embeddingClient.model(),
                cfg.topK() * cfg.candidateMultiplier());

        Set<String> activeServices = normalize(active.affectedServices());

        List<HistoricalIncidentMatch> ranked = candidates.stream()
                .map(m -> m.withScore(score(m, activeServices, cfg)))
                .filter(m -> m.similarity() >= cfg.minSimilarity())
                .sorted(Comparator.comparingDouble(HistoricalIncidentMatch::finalScore).reversed())
                .limit(cfg.topK())
                .collect(Collectors.toList());

        // Belt and braces: a cross-tenant row here means something upstream
        // is broken, and it must never reach a prompt.
        ranked.removeIf(m -> !m.organizationId().equals(active.organizationId()));

        if (!ranked.isEmpty()) {
            repository.recordRetrieval(active.organizationId(), active.incidentId(), ranked);
        }

        log.info("incident {}: {} candidates -> {} historical matches (best similarity {})",
                active.incidentId(), candidates.size(), ranked.size(),
                ranked.isEmpty() ? "n/a" : String.format("%.3f", ranked.get(0).similarity()));

        return ranked;
    }

    private double score(HistoricalIncidentMatch m,
                         Set<String> activeServices,
                         IncidentMemoryProperties.Retrieval cfg) {

        double overlap = jaccard(activeServices, normalize(m.serviceNames()));
        double recency = recencyScore(m.resolvedAt(), cfg.maxAge());

        return cfg.weightSimilarity() * m.similarity()
                + cfg.weightServiceOverlap() * overlap
                + cfg.weightRecency() * recency;
    }

    /** Linear decay to 0 at maxAge. Last month's fix beats the same fix from two years ago. */
    private static double recencyScore(Instant resolvedAt, Duration maxAge) {
        long ageSeconds = Duration.between(resolvedAt, Instant.now()).getSeconds();
        if (ageSeconds <= 0) {
            return 1.0;
        }
        double ratio = (double) ageSeconds / maxAge.getSeconds();
        return Math.max(0.0, 1.0 - ratio);
    }

    private static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersection.size() / union.size();
    }

    private static Set<String> normalize(List<String> values) {
        if (values == null) {
            return Set.of();
        }
        return values.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT).trim())
                .collect(Collectors.toSet());
    }
}
