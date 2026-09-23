package com.acme.incident.memory.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Everything Phase 7 needs from an incident, flattened.
 *
 * Deliberately a view rather than your JPA entity: memory should not be
 * coupled to the phase 1-6 aggregate, and this keeps the embedded document
 * stable when that aggregate changes shape.
 */
public record ResolvedIncidentView(
        UUID incidentId,
        UUID organizationId,
        String title,
        String severity,
        List<String> affectedServices,
        Instant detectedAt,
        Instant resolvedAt,
        List<Symptom> symptoms,
        String rootCause,
        List<String> contributingFactors,
        String resolutionSummary,
        List<String> recommendations,
        String confidenceLevel,
        Double confidenceScore) {

    public record Symptom(String source, String signal, String detail) { }

    /**
     * A LOW-confidence, inconclusive RCA teaches future retrieval nothing useful
     * and actively risks being cited as false precedent (see
     * HistoricalContextRenderer's prompt instructions, which explicitly warn the
     * RCA model against assuming a shared cause - that warning is undermined if
     * the "precedent" itself was never confident to begin with).
     *
     * Gate: require MEDIUM or HIGH confidenceLevel. Missing/unparsed confidence
     * data is treated as not rememberable (fail closed, not open) - an incident
     * whose confidence we cannot verify should not enter memory as if it were
     * reliable precedent.
     */
    private static final List<String> REMEMBERABLE_CONFIDENCE_LEVELS = List.of("MEDIUM", "HIGH");

    public boolean isRememberable() {
        return resolvedAt != null
                && rootCause != null && !rootCause.isBlank()
                && resolutionSummary != null && !resolutionSummary.isBlank()
                && confidenceLevel != null
                && REMEMBERABLE_CONFIDENCE_LEVELS.contains(confidenceLevel.trim().toUpperCase());
    }
}