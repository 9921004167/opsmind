package com.opsmind.core.investigation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * A single OBSERVED FACT collected from a real source (Prometheus, Tempo, ...).
 * Evidence rows are never AI-generated - they represent what was actually measured
 * or observed, at observedAt. The AI (GeminiAIInvestigator) reasons OVER these rows
 * but never writes to this table; that separation is what keeps "observed" and
 * "hypothesis" honest (see RcaFinding).
 */
@Entity
@Table(name = "evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Evidence {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "investigation_id", nullable = false)
    private UUID investigationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EvidenceType type;

    /** e.g. "prometheus", "tempo". Free text, not an enum - collectors are pluggable. */
    @Column(nullable = false)
    private String source;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    /** String representation of the measured value, e.g. "23.4%", "512", "true".
     *  Kept as a string since evidence types vary widely (a rate, a trace count, a
     *  boolean up/down) - the description/title carry the units and context. */
    @Column(name = "observed_value")
    private String observedValue;

    /** Raw supporting data (e.g. the exact PromQL query run, or a trace ID list) -
     *  kept for traceability/audit of what was actually queried, not for the AI to
     *  parse structurally. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (observedAt == null) observedAt = createdAt;
    }
}
