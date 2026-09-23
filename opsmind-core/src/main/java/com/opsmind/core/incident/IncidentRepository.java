package com.opsmind.core.incident;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IncidentRepository extends JpaRepository<Incident, UUID> {
    List<Incident> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);
    Optional<Incident> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /**
     * Correlation candidates: open incidents for the same
     * org+project+environment+service, most recent first. The caller still
     * has to check alert_type via the linked Alert - this table has no
     * alert_type column of its own, since an incident can end up correlating
     * alerts of the same type by design, never a different one.
     */
    @Query("SELECT i FROM Incident i WHERE i.organizationId = :organizationId " +
           "AND i.projectId = :projectId AND i.environmentId = :environmentId " +
           "AND i.primaryServiceId = :serviceId " +
           "AND i.status NOT IN (com.opsmind.core.incident.IncidentStatus.RESOLVED, " +
           "com.opsmind.core.incident.IncidentStatus.ESCALATED, com.opsmind.core.incident.IncidentStatus.CLOSED) " +
           "ORDER BY i.createdAt DESC")
    List<Incident> findOpenByServiceForCorrelation(
            @Param("organizationId") UUID organizationId,
            @Param("projectId") UUID projectId,
            @Param("environmentId") UUID environmentId,
            @Param("serviceId") UUID serviceId);
}