package com.opsmind.core.remediation.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationRecommendedEvent(
        UUID recommendationId, UUID incidentId, String riskLevel, boolean manualOnly, Instant createdAt) {}
