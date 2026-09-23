package com.opsmind.core.alert.dto;

import com.opsmind.core.alert.AlertSeverity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The generic alert ingestion contract (Section 6). Deliberately provider-agnostic:
 * "source" identifies where it came from, but no field here is specific to any one
 * monitoring vendor.
 */
public record AlertIngestRequest(
        @NotNull UUID projectId,
        @NotNull UUID environmentId,
        UUID serviceId,
        @NotBlank String source,
        @NotBlank String alertType,
        @NotNull AlertSeverity severity,
        @NotBlank String title,
        String description,
        String metricName,
        BigDecimal thresholdValue,
        BigDecimal currentValue,
        Instant timestamp,
        Map<String, Object> metadata
) {}
