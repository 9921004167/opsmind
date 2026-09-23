package com.opsmind.core.remediation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RemediationVerificationRepository extends JpaRepository<RemediationVerification, UUID> {
    List<RemediationVerification> findByRecommendationIdOrderByVerifiedAtDesc(UUID recommendationId);
}
