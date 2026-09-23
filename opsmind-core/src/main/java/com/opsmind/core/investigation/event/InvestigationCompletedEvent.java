package com.opsmind.core.investigation.event;

import java.time.Instant;
import java.util.UUID;

public record InvestigationCompletedEvent(UUID investigationId, UUID incidentId, String confidenceLevel, Instant completedAt) {}
