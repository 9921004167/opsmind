package com.opsmind.core.incident.event;

import com.opsmind.core.incident.IncidentStatus;

import java.time.Instant;
import java.util.UUID;

public record IncidentStatusChangedEvent(
        UUID incidentId,
        String incidentNumber,
        IncidentStatus previousStatus,
        IncidentStatus newStatus,
        Instant changedAt
) {}
