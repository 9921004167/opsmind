package com.opsmind.core.remediation;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per approve/reject decision. Even an auto-approval (Section 11)
 * creates a real row here with autoApproved=true and actorUserId=null - the
 * spec explicitly requires this to stay fully auditable.
 */
@Entity
@Table(name = "remediation_approvals")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemediationApproval {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ApprovalDecision decision;

    /** Null only when autoApproved=true - a human decision always has an actor. */
    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "auto_approved", nullable = false)
    private boolean autoApproved;

    @Column(columnDefinition = "text")
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
