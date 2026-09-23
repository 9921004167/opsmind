package com.opsmind.core.remediation;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** One row per execution attempt. Failures are persisted, not discarded - a
 *  failed execution is real history, exactly like a failed Investigation. */
@Entity
@Table(name = "remediation_executions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemediationExecution {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "executor_type", nullable = false)
    private ExecutorType executorType;

    @Column(nullable = false)
    private boolean succeeded;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @PrePersist
    void prePersist() {
        if (startedAt == null) startedAt = Instant.now();
    }
}
