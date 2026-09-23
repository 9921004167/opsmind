package com.opsmind.core.remediation.service;

import com.opsmind.core.remediation.RunbookDefinition;
import com.opsmind.core.remediation.RunbookDefinitionRepository;
import com.opsmind.core.remediation.dto.RunbookResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Read access to the shared/global runbook catalog (Section 9) - not tenant data. */
@Service
@RequiredArgsConstructor
public class RunbookCatalogService {

    private final RunbookDefinitionRepository runbookDefinitionRepository;

    @Transactional(readOnly = true)
    public List<RunbookResponse> listAll() {
        return runbookDefinitionRepository.findAllByOrderByTitleAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<RunbookDefinition> listAllEntities() {
        return runbookDefinitionRepository.findAllByOrderByTitleAsc();
    }

    @Transactional(readOnly = true)
    public Optional<RunbookDefinition> findByKey(String key) {
        return runbookDefinitionRepository.findByKey(key);
    }

    private RunbookResponse toResponse(RunbookDefinition r) {
        return new RunbookResponse(r.getId(), r.getKey(), r.getTitle(), r.getDescription(),
                r.getRiskLevel(), r.getExecutorType(), r.getTargetServiceSlug());
    }
}
