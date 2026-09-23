package com.opsmind.core.incident;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface IncidentAlertLinkRepository extends JpaRepository<IncidentAlertLink, UUID> {
    List<IncidentAlertLink> findByIncidentId(UUID incidentId);
}
