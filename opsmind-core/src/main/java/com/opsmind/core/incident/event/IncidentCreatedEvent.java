package com.opsmind.core.incident.event;

import com.opsmind.core.incident.IncidentSeverity;

import java.time.Instant;
import java.util.UUID;

public record IncidentCreatedEvent(
        UUID incidentId,
        String incidentNumber,
        UUID projectId,
        UUID environmentId,
        UUID primaryServiceId,
        String title,
        IncidentSeverity severity,
        UUID sourceAlertId,
        Instant createdAt
) {}
