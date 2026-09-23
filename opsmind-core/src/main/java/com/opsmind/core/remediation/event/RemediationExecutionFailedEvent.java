package com.opsmind.core.remediation.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationExecutionFailedEvent(
        UUID recommendationId, UUID incidentId, String reason, Instant failedAt) {}
