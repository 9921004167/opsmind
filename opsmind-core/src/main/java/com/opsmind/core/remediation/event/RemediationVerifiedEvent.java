package com.opsmind.core.remediation.event;

import java.time.Instant;
import java.util.UUID;

public record RemediationVerifiedEvent(
        UUID recommendationId, UUID incidentId, String result, Instant verifiedAt) {}
