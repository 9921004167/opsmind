package com.opsmind.core.investigation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvestigationRepository extends JpaRepository<Investigation, UUID> {
    List<Investigation> findByIncidentIdOrderByCreatedAtDesc(UUID incidentId);
    Optional<Investigation> findByIdAndOrganizationId(UUID id, UUID organizationId);

    // Added for Phase 8: remediation recommendations are built from the most
    // recent COMPLETED investigation's RcaFinding.
    Optional<Investigation> findFirstByIncidentIdAndStatusOrderByCreatedAtDesc(UUID incidentId, InvestigationStatus status);
}
