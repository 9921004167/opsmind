package com.opsmind.core.remediation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RemediationExecutionRepository extends JpaRepository<RemediationExecution, UUID> {
    List<RemediationExecution> findByRecommendationIdOrderByStartedAtDesc(UUID recommendationId);
}
