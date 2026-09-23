package com.opsmind.core.incident.dto;

import com.opsmind.core.incident.IncidentStatus;
import jakarta.validation.constraints.NotNull;

public record IncidentStatusUpdateRequest(@NotNull IncidentStatus status, String note) {}
