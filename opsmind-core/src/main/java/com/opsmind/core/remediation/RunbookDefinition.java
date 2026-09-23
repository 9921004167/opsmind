package com.opsmind.core.remediation;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The CLOSED catalog of runbooks Gemini is allowed to select from (Section 9).
 * This is a shared/global platform table - NOT tenant data. Deliberately has
 * no organization_id: every organization sees and is offered the same catalog.
 *
 * executorType + executionEndpoint together define what actually happens on
 * execute(): FAULT_DISABLE rows carry a real endpoint (Phase 4's
 * /faults/{service}/disable); MANUAL rows carry no endpoint and can never be
 * executed automatically, no matter what risk level they carry.
 */
@Entity
@Table(name = "runbook_definitions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RunbookDefinition {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, unique = true)
    private String key;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_level", nullable = false)
    private RiskLevel riskLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "executor_type", nullable = false)
    private ExecutorType executorType;

    /** Only populated for FAULT_DISABLE rows - the full URL of the Phase 4
     *  fault-disable endpoint this runbook triggers. Null for MANUAL rows. */
    @Column(name = "execution_endpoint")
    private String executionEndpoint;

    /** Slug of the service this runbook targets, for display/audit only -
     *  never used to derive an endpoint (executionEndpoint is authoritative). */
    @Column(name = "target_service_slug")
    private String targetServiceSlug;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void prePersist() {
        if (createdAt == null) createdAt = Instant.now();
    }
}
