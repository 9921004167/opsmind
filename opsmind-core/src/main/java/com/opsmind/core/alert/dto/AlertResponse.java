package com.opsmind.core.alert.dto;

import com.opsmind.core.alert.AlertSeverity;
import com.opsmind.core.alert.AlertStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AlertResponse(
        UUID id,
        UUID projectId,
        UUID environmentId,
        UUID serviceId,
        String source,
        String alertType,
        AlertSeverity severity,
        AlertStatus status,
        String title,
        String description,
        String metricName,
        BigDecimal thresholdValue,
        BigDecimal currentValue,
        Instant receivedAt,
        UUID createdIncidentId,
        String createdIncidentNumber
) {}
