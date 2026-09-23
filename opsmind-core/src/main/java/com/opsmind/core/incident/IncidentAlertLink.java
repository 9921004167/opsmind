package com.opsmind.core.incident;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Links an Alert to the Incident it was folded into. A dedicated join table (rather
 * than a single alert.incident_id FK) exists because Phase 6 correlation will link
 * MANY alerts to ONE incident - Phase 1 only ever creates a single link per alert,
 * but the structure is already many-to-one-ready.
 */
@Entity
@Table(name = "incident_alerts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentAlertLink {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "alert_id", nullable = false)
    private UUID alertId;

    /** Phase 1: always "naive_one_to_one". Reserved values for Phase 6: e.g. "service+time_window", "error_signature". */
    @Column(name = "correlation_reason", nullable = false)
    private String correlationReason;

    @Column(name = "linked_at", nullable = false)
    private Instant linkedAt;

    @PrePersist
    void prePersist() {
        if (linkedAt == null) {
            linkedAt = Instant.now();
        }
    }
}
