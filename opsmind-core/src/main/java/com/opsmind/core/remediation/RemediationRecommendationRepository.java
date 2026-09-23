package com.opsmind.core.remediation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RemediationRecommendationRepository extends JpaRepository<RemediationRecommendation, UUID> {
    List<RemediationRecommendation> findByIncidentIdAndOrganizationIdOrderByCreatedAtDesc(UUID incidentId, UUID organizationId);
    Optional<RemediationRecommendation> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
