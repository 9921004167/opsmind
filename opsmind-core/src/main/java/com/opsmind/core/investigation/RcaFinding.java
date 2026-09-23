package com.opsmind.core.investigation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The AI's structured HYPOTHESIS + RECOMMENDATION, always traceable back to the
 * Evidence rows it was given (supportingEvidenceIds). This table is the only place
 * AI-generated content is persisted - it is never mixed into the Evidence table,
 * so "what OpsMind observed" and "what the AI concluded" are always distinguishable
 * by which table a row lives in, not just a field within one table.
 */
@Entity
@Table(name = "rca_findings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RcaFinding {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "investigation_id", nullable = false, unique = true)
    private UUID investigationId;

    @Column(name = "root_cause", nullable = false, columnDefinition = "text")
    private String rootCause;

    @Column(columnDefinition = "text")
    private String hypothesis;

    @Enumerated(EnumType.STRING)
    @Column(name = "confidence_level", nullable = false)
    private RcaConfidence confidenceLevel;

    /** Optional numeric 0.0-1.0 companion to confidenceLevel, if the model provided one. */
    @Column(name = "confidence_score")
    private Double confidenceScore;

    @Column(name = "affected_service")
    private String affectedService;

    @Column(columnDefinition = "text")
    private String impact;

    /** JSON array of Evidence UUIDs (as strings) the AI was actually given and cited. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "supporting_evidence_ids", columnDefinition = "jsonb", nullable = false)
    private String supportingEvidenceIds;

    @Column(name = "reasoning_summary", columnDefinition = "text")
    private String reasoningSummary;

    /** JSON array of recommended action strings - NOT executable commands (Section
     *  "AI safety": recommendations are text for a human to act on, never parsed
     *  or run as commands anywhere in this codebase). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "recommended_actions", columnDefinition = "jsonb", nullable = false)
    private String recommendedActions;

    @Column(name = "model_provider", nullable = false)
    private String modelProvider;

    @Column(name = "model_name", nullable = false)
    private String modelName;

    @Column(name = "generated_at", nullable = false)
    private Instant generatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
        if (generatedAt == null) generatedAt = createdAt;
    }
}
