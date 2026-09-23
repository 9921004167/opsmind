package com.opsmind.core.alert;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A normalized, source-agnostic representation of a machine-generated alert
 * (Section 6/7 of the spec). Raw provider-specific payloads are preserved in
 * rawPayload for traceability, but every field OpsMind reasons about is normalized.
 */
@Entity
@Table(name = "alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Alert {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "environment_id", nullable = false)
    private UUID environmentId;

    @Column(name = "service_id")
    private UUID serviceId;

    /** Free-text origin, e.g. "prometheus", "manual", "custom-webhook". Not an enum - providers are pluggable. */
    @Column(nullable = false)
    private String source;

    @Column(name = "alert_type", nullable = false)
    private String alertType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "metric_name")
    private String metricName;

    @Column(name = "threshold_value")
    private BigDecimal thresholdValue;

    @Column(name = "current_value")
    private BigDecimal currentValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
        if (receivedAt == null) {
            receivedAt = Instant.now();
        }
        if (status == null) {
            status = AlertStatus.RECEIVED;
        }
    }
}
