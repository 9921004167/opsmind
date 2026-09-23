package com.opsmind.core.remediation;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The AI's remediation suggestion, always reduced to one of two safe shapes:
 *  - a runbookId pointing at a real, validated RunbookDefinition (executable,
 *    subject to approval), or
 *  - manualOnly = true with only free-text guidance (aiRecommendationText),
 *    which is NEVER converted into anything executable anywhere in this codebase.
 *
 * riskLevel is always copied from the matched RunbookDefinition (or HIGH, if
 * unmatched) - never computed from aiRecommendationText or Gemini's own opinion.
 */
@Entity
@Table(name = "remediation_recommendations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemediationRecommendation {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "rca_finding_id")
    private UUID rcaFindingId;

    /** Null when the AI's suggested key didn't match anything in the closed
     *  catalog, or when it recommended no runbook at all. */
    @Column(name = "runbook_id")
    private UUID runbookId;

    /** Denormalized copy of the matched runbook's key, kept even if the runbook
     *  row is later changed, purely for audit/history readability. Null if unmatched. */
    @Column(name = "matched_runbook_key")
    private String matchedRunbookKey;

    /** The raw key string Gemini returned, verbatim, even when it did not match
     *  the catalog - so a hallucinated key is never silently discarded from history. */
    @Column(name = "ai_suggested_key")
    private String aiSuggestedKey;

    @Column(name = "ai_recommendation_text", columnDefinition = "text")
    private String aiRecommendationText;

    @Column(name = "ai_rationale", columnDefinition = "text")
    private String aiRationale;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    /** true whenever there is no safe, catalog-validated executable path:
     *  unmatched/hallucinated key, or a matched runbook whose executorType is
     *  MANUAL. Checked by RemediationExecutionService before every execute(). */
    @Column(name = "manual_only", nullable = false)
    private boolean manualOnly;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecommendationStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "ai_raw_response", columnDefinition = "jsonb")
    private String aiRawResponse;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
        if (status == null) status = RecommendationStatus.PROPOSED;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = Instant.now();
    }
}
