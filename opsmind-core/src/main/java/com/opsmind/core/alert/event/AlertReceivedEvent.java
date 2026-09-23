package com.opsmind.core.alert.event;

import com.opsmind.core.alert.AlertSeverity;

import java.time.Instant;
import java.util.UUID;

public record AlertReceivedEvent(
        UUID alertId,
        UUID projectId,
        UUID environmentId,
        UUID serviceId,
        String source,
        String alertType,
        AlertSeverity severity,
        String title,
        Instant receivedAt
) {}
