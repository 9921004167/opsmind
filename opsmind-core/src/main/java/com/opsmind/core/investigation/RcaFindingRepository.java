package com.opsmind.core.investigation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RcaFindingRepository extends JpaRepository<RcaFinding, UUID> {
    Optional<RcaFinding> findByInvestigationId(UUID investigationId);
}
