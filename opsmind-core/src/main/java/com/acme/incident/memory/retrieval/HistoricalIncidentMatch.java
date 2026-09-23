package com.acme.incident.memory.retrieval;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A retrieved historical incident.
 *
 * similarity  - raw cosine similarity, 0..1
 * finalScore  - after reranking (service overlap + recency)
 */
public record HistoricalIncidentMatch(
        UUID incidentId,
        UUID organizationId,
        String content,
        List<String> serviceNames,
        String severity,
        Instant resolvedAt,
        double similarity,
        double finalScore) {

    public HistoricalIncidentMatch withScore(double score) {
        return new HistoricalIncidentMatch(
                incidentId, organizationId, content, serviceNames,
                severity, resolvedAt, similarity, score);
    }
}
