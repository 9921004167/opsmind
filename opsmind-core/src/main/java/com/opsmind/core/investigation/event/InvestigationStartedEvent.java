package com.opsmind.core.investigation.event;

import java.time.Instant;
import java.util.UUID;

public record InvestigationStartedEvent(UUID investigationId, UUID incidentId, Instant startedAt) {}
