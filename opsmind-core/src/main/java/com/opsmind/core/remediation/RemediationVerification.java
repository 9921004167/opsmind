package com.opsmind.core.remediation;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/** Result of checking whether an executed remediation actually resolved the
 *  symptom, using the same real Prometheus query path as Phase 6 evidence
 *  collection (PrometheusEvidenceCollector) - never a duplicated query. */
@Entity
@Table(name = "remediation_verifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RemediationVerification {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "recommendation_id", nullable = false)
    private UUID recommendationId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationResult result;

    @Column(name = "metric_name")
    private String metricName;

    @Column(name = "observed_value")
    private String observedValue;

    @Column(columnDefinition = "text")
    private String detail;

    @Column(name = "verified_at", nullable = false)
    private Instant verifiedAt;

    @PrePersist
    void prePersist() {
        if (verifiedAt == null) verifiedAt = Instant.now();
    }
}
