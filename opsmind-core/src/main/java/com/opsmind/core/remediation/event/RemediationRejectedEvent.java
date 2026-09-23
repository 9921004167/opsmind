package com.opsmind.core.remediation.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationRejectedEvent(
        UUID recommendationId, UUID incidentId, String reason, Instant rejectedAt) {}
