package com.opsmind.core.incident.dto;

import com.opsmind.core.incident.IncidentEventType;

import java.time.Instant;
import java.util.UUID;

public record IncidentTimelineEntryResponse(
        UUID id,
        IncidentEventType eventType,
        String description,
        String actor,
        Instant occurredAt
) {}
