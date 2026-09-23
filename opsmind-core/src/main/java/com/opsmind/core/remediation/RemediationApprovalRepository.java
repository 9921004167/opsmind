package com.opsmind.core.remediation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RemediationApprovalRepository extends JpaRepository<RemediationApproval, UUID> {
    List<RemediationApproval> findByRecommendationIdOrderByCreatedAtDesc(UUID recommendationId);
}
