package com.opsmind.core.tenant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectEnvironmentRepository extends JpaRepository<ProjectEnvironment, UUID> {
    List<ProjectEnvironment> findByProjectId(UUID projectId);
    Optional<ProjectEnvironment> findByProjectIdAndType(UUID projectId, EnvironmentType type);
}
