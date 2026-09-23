package com.acme.incident.memory.ingest;

import com.acme.incident.memory.document.IncidentMemoryDocumentBuilder;
import com.acme.incident.memory.embedding.GeminiEmbeddingClient;
import com.acme.incident.memory.port.IncidentMemorySourcePort;
import com.acme.incident.memory.port.ResolvedIncidentView;
import com.acme.incident.memory.store.IncidentMemoryRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Writes the queue row when an incident is resolved.
 *
 * AFTER_COMMIT is load-bearing: enqueue inside the resolving transaction and
 * the worker can claim the row before the incident is visible to it.
 */
@Component
public class IncidentMemoryEnqueuer {

    private static final Logger log = LoggerFactory.getLogger(IncidentMemoryEnqueuer.class);

    private final IncidentMemorySourcePort source;
    private final IncidentMemoryDocumentBuilder builder;
    private final IncidentMemoryRepository repository;
    private final GeminiEmbeddingClient embeddingClient;

    public IncidentMemoryEnqueuer(IncidentMemorySourcePort source,
                                  IncidentMemoryDocumentBuilder builder,
                                  IncidentMemoryRepository repository,
                                  GeminiEmbeddingClient embeddingClient) {
        this.source = source;
        this.builder = builder;
        this.repository = repository;
        this.embeddingClient = embeddingClient;
    }

  
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onIncidentResolved(IncidentResolvedEvent event) {
        Optional<ResolvedIncidentView> maybe =
                source.loadResolved(event.organizationId(), event.incidentId());

        if (maybe.isEmpty()) {
            log.warn("incident {} not found for memory ingest", event.incidentId());
            return;
        }

        ResolvedIncidentView inc = maybe.get();

        if (!inc.isRememberable()) {
            log.debug("incident {} resolved without RCA or resolution summary; not remembered",
                    inc.incidentId());
            return;
        }

        String content = builder.buildDocument(inc);

        repository.upsertPending(
                inc.incidentId(),
                inc.organizationId(),
                content,
                sha256(content),
                inc.affectedServices(),
                inc.severity(),
                inc.resolvedAt(),
                embeddingClient.model());

        log.info("queued incident {} for organizational memory ({} chars)",
                inc.incidentId(), content.length());
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}