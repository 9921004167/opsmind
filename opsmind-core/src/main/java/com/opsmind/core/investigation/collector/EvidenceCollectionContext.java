package com.opsmind.core.investigation.collector;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything a collector needs to scope its query - deliberately narrow (org/
 * project/environment/service + a bounded time window) so no collector can query
 * unlimited historical data (spec requirement).
 */
public record EvidenceCollectionContext(
        UUID investigationId,
        UUID organizationId,
        UUID projectId,
        UUID environmentId,
        UUID serviceId,
        String serviceSlug,    // nullable if the incident has no primary service
        Instant windowStart,
        Instant windowEnd
) {}
