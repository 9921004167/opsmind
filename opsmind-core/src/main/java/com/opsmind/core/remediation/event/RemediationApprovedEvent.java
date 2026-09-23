package com.opsmind.core.remediation.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationApprovedEvent(
        UUID recommendationId, UUID incidentId, boolean autoApproved, Instant approvedAt) {}
