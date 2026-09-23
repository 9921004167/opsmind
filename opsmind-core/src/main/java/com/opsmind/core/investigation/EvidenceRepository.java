package com.opsmind.core.investigation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EvidenceRepository extends JpaRepository<Evidence, UUID> {
    List<Evidence> findByInvestigationIdOrderByObservedAtAsc(UUID investigationId);
}
