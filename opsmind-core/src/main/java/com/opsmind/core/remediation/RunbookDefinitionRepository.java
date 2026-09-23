package com.opsmind.core.remediation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RunbookDefinitionRepository extends JpaRepository<RunbookDefinition, UUID> {
    Optional<RunbookDefinition> findByKey(String key);
    List<RunbookDefinition> findAllByOrderByTitleAsc();
}
