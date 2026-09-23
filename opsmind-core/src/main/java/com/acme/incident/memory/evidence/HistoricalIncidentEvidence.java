package com.acme.incident.memory.evidence;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A retrieved incident expressed in the same shape as your Phase 3-5
 * evidence items, so the Phase 6 investigator treats it uniformly.
 *
 * Map this onto your existing Evidence type with
 * type = HISTORICAL_INCIDENT. Confidence carries the rerank score, which
 * lets the RCA prompt weigh a 0.91 match differently from a 0.64 one.
 */
public record HistoricalIncidentEvidence(
        UUID historicalIncidentId,
        String title,
        String severity,
        List<String> affectedServices,
        Instant resolvedAt,
        String rootCause,
        String resolution,
        List<String> recommendations,
        double similarity,
        double confidence) {

    public static final String EVIDENCE_TYPE = "HISTORICAL_INCIDENT";
}
