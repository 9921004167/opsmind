package com.opsmind.core.investigation.event;

import java.time.Instant;
import java.util.UUID;

public record InvestigationFailedEvent(UUID investigationId, UUID incidentId, String failureReason, Instant failedAt) {}
