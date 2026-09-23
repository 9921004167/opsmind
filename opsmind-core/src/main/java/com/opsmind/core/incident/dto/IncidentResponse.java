package com.opsmind.core.incident.dto;

import com.opsmind.core.incident.IncidentSeverity;
import com.opsmind.core.incident.IncidentStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record IncidentResponse(
        UUID id,
        String incidentNumber,
        UUID projectId,
        UUID environmentId,
        UUID primaryServiceId,
        String title,
        IncidentSeverity severity,
        IncidentStatus status,
        String summary,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        Instant closedAt,
        Set<IncidentStatus> allowedNextStatuses
) {}
