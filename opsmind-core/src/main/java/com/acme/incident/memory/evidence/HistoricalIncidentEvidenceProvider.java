package com.acme.incident.memory.evidence;

import com.acme.incident.memory.port.ActiveIncidentView;
import com.acme.incident.memory.port.IncidentMemorySourcePort;
import com.acme.incident.memory.port.ResolvedIncidentView;
import com.acme.incident.memory.retrieval.HistoricalIncidentMatch;
import com.acme.incident.memory.retrieval.IncidentMemoryRetrievalService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Entry point for Phase 6. Call this while assembling evidence for an
 * investigation and append the results to the evidence list.
 *
 * Re-reads each match from the source of truth rather than parsing the
 * embedded document back apart: the stored `content` is an artifact built
 * for the embedding model, not a record.
 */
@Component
public class HistoricalIncidentEvidenceProvider {

    private static final Logger log =
            LoggerFactory.getLogger(HistoricalIncidentEvidenceProvider.class);

    private final IncidentMemoryRetrievalService retrieval;
    private final IncidentMemorySourcePort source;

    public HistoricalIncidentEvidenceProvider(IncidentMemoryRetrievalService retrieval,
                                              IncidentMemorySourcePort source) {
        this.retrieval = retrieval;
        this.source = source;
    }

    public List<HistoricalIncidentEvidence> collect(ActiveIncidentView active) {
        List<HistoricalIncidentMatch> matches = retrieval.findSimilar(active);
        UUID org = active.organizationId();

        return matches.stream()
                .map(m -> toEvidence(org, m))
                .flatMap(Optional::stream)
                .toList();
    }

    private Optional<HistoricalIncidentEvidence> toEvidence(UUID org, HistoricalIncidentMatch m) {
        Optional<ResolvedIncidentView> maybe = source.loadResolved(org, m.incidentId());
        if (maybe.isEmpty()) {
            log.warn("memory row {} has no backing incident; skipping", m.incidentId());
            return Optional.empty();
        }
        ResolvedIncidentView inc = maybe.get();
        return Optional.of(new HistoricalIncidentEvidence(
                inc.incidentId(),
                inc.title(),
                inc.severity(),
                inc.affectedServices(),
                inc.resolvedAt(),
                inc.rootCause(),
                inc.resolutionSummary(),
                inc.recommendations(),
                m.similarity(),
                m.finalScore()));
    }
}
