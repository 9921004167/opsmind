package com.opsmind.core.remediation.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationExecutedEvent(
        UUID recommendationId, UUID incidentId, String runbookKey, Instant executedAt) {}
