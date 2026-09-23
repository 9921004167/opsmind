package com.acme.incident.memory.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The query side: a live incident that has symptoms and correlated evidence
 * but no root cause yet.
 */
public record ActiveIncidentView(
        UUID incidentId,
        UUID organizationId,
        String title,
        String severity,
        List<String> affectedServices,
        Instant detectedAt,
        List<ResolvedIncidentView.Symptom> symptoms,
        List<String> evidenceSummaries) { }
