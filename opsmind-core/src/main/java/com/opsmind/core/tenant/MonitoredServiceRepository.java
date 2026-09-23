package com.opsmind.core.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MonitoredServiceRepository extends JpaRepository<MonitoredService, UUID> {
    List<MonitoredService> findByProjectId(UUID projectId);
    Optional<MonitoredService> findByProjectIdAndSlug(UUID projectId, String slug);
}
