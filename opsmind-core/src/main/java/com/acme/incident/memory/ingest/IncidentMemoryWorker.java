package com.acme.incident.memory.ingest;

import com.acme.incident.memory.config.IncidentMemoryProperties;
import com.acme.incident.memory.embedding.EmbeddingException;
import com.acme.incident.memory.embedding.EmbeddingTask;
import com.acme.incident.memory.embedding.GeminiEmbeddingClient;
import com.acme.incident.memory.store.IncidentMemoryRepository;
import com.acme.incident.memory.store.PendingMemory;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Database-backed queue consumer. No broker: the incident_memory row IS the
 * queue entry, the retry counter and the dead-letter record.
 *
 * Claiming and embedding are separate transactions on purpose. The claim
 * commits immediately so a slow Gemini call never holds a row lock, and a
 * crash mid-call leaves the row in PROCESSING for the sweeper to reclaim.
 */
@Component
public class IncidentMemoryWorker {

    private static final Logger log = LoggerFactory.getLogger(IncidentMemoryWorker.class);

    private final IncidentMemoryRepository repository;
    private final GeminiEmbeddingClient embeddingClient;
    private final IncidentMemoryProperties props;

    public IncidentMemoryWorker(IncidentMemoryRepository repository,
                                GeminiEmbeddingClient embeddingClient,
                                IncidentMemoryProperties props) {
        this.repository = repository;
        this.embeddingClient = embeddingClient;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${incident-memory.worker.poll-interval:10000}")
    public void pollAndEmbed() {
        List<PendingMemory> batch = claim();
        if (batch.isEmpty()) {
            return;
        }
        log.debug("embedding {} incident memories", batch.size());
        for (PendingMemory row : batch) {
            process(row);
        }
    }

    @Transactional
    protected List<PendingMemory> claim() {
        return repository.claimBatch(embeddingClient.model());
    }

    private void process(PendingMemory row) {
        try {
            float[] vector = embeddingClient.embed(row.content(), EmbeddingTask.DOCUMENT);
            repository.markReady(row.id(), vector);
        } catch (EmbeddingException e) {
            if (!e.isRetryable()) {
                // Malformed input will fail identically forever. Park it at
                // max attempts so it stops consuming the batch.
                log.error("permanent embedding failure for incident {}: {}",
                        row.incidentId(), e.getMessage());
                repository.markFailed(row.id(), e.getMessage(), Duration.ofDays(3650));
                return;
            }
            Duration backoff = backoffFor(row.attempts() + 1);
            log.warn("embedding failed for incident {} (attempt {}), retrying in {}s",
                    row.incidentId(), row.attempts() + 1, backoff.toSeconds());
            repository.markFailed(row.id(), e.getMessage(), backoff);
        } catch (Exception e) {
            repository.markFailed(row.id(), e.toString(), backoffFor(row.attempts() + 1));
        }
    }

    /** Exponential backoff: 30s, 60s, 120s, 240s, 480s. */
    private Duration backoffFor(int attempt) {
        long base = props.worker().baseBackoff().toSeconds();
        return Duration.ofSeconds(base * (1L << Math.min(attempt - 1, 6)));
    }

    /**
     * Reclaim rows stuck in PROCESSING because an instance died mid-call.
     * Runs far less often than the main poll.
     */
    @Scheduled(fixedDelay = 300_000)
    @Transactional
    public void reclaimStuck() {
        int n = repository.reclaimStale(Duration.ofMinutes(15));
        if (n > 0) {
            log.warn("reclaimed {} incident memory rows stuck in PROCESSING", n);
        }
    }
}
