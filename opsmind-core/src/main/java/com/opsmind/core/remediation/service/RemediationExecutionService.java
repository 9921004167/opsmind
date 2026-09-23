package com.opsmind.core.remediation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.DomainException;
import com.opsmind.core.common.NotFoundException;
import com.opsmind.core.kafka.EventEnvelope;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.remediation.*;
import com.opsmind.core.remediation.dto.RemediationRecommendationResponse;
import com.opsmind.core.remediation.event.RemediationExecutedEvent;
import com.opsmind.core.remediation.event.RemediationExecutionFailedEvent;
import com.opsmind.core.remediation.event.RemediationTopics;
import com.opsmind.core.remediation.executor.RemediationExecutor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

@Service
@Slf4j
public class RemediationExecutionService {

    private final RemediationRecommendationRepository recommendationRepository;
    private final RemediationExecutionRepository executionRepository;
    private final RunbookDefinitionRepository runbookDefinitionRepository;
    private final RemediationVerificationService verificationService;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final Map<ExecutorType, RemediationExecutor> executorsByType;

    public RemediationExecutionService(RemediationRecommendationRepository recommendationRepository,
                                        RemediationExecutionRepository executionRepository,
                                        RunbookDefinitionRepository runbookDefinitionRepository,
                                        RemediationVerificationService verificationService,
                                        EventPublisher eventPublisher,
                                        AuditService auditService,
                                        ObjectMapper objectMapper,
                                        List<RemediationExecutor> executors) {
        this.recommendationRepository = recommendationRepository;
        this.executionRepository = executionRepository;
        this.runbookDefinitionRepository = runbookDefinitionRepository;
        this.verificationService = verificationService;
        this.eventPublisher = eventPublisher;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
        this.executorsByType = executors.stream()
                .collect(java.util.stream.Collectors.toMap(RemediationExecutor::type, Function.identity()));
    }

    @Transactional
    public RemediationRecommendationResponse execute(UUID organizationId, UUID recommendationId, UUID actorUserId, RecommendationResponseAssembler assembler) {
        RemediationRecommendation recommendation = recommendationRepository.findByIdAndOrganizationId(recommendationId, organizationId)
                .orElseThrow(() -> new NotFoundException("Remediation recommendation not found: " + recommendationId));

        if (recommendation.isManualOnly()) {
            throw new DomainException("manual_only: this recommendation has no safe automated executor and must be performed by a human");
        }
        if (recommendation.getRunbookId() == null) {
            throw new DomainException("no_runbook_matched: this recommendation cannot be executed");
        }

        RemediationStateMachine.validateTransition(recommendation.getStatus(), RecommendationStatus.EXECUTING);

        UUID runbookId = recommendation.getRunbookId();
        RunbookDefinition runbook = runbookDefinitionRepository.findById(runbookId)
                .orElseThrow(() -> new DomainException("runbook_not_found: " + runbookId));

        recommendation.setStatus(RecommendationStatus.EXECUTING);
        recommendation = recommendationRepository.save(recommendation);

        RemediationExecution execution = RemediationExecution.builder()
                .recommendationId(recommendation.getId())
                .organizationId(organizationId)
                .executorType(runbook.getExecutorType())
                .actorUserId(actorUserId)
                .succeeded(false)
                .startedAt(Instant.now())
                .build();

        RemediationExecutor executor = executorsByType.get(runbook.getExecutorType());
        RemediationExecutor.ExecutionResult result = executor == null
                ? new RemediationExecutor.ExecutionResult(false, "no_executor_registered_for_type: " + runbook.getExecutorType())
                : executor.execute(runbook);

        execution.setSucceeded(result.success());
        execution.setDetail(result.detail());
        execution.setCompletedAt(Instant.now());
        executionRepository.save(execution);

        String auditMetadataJson = toAuditMetadataJson(result.detail());

        if (result.success()) {
            RemediationStateMachine.validateTransition(recommendation.getStatus(), RecommendationStatus.EXECUTED);
            recommendation.setStatus(RecommendationStatus.EXECUTED);
            recommendation = recommendationRepository.save(recommendation);

            eventPublisher.publish(RemediationTopics.REMEDIATION_EXECUTED, recommendation.getId().toString(),
                    EventEnvelope.of(RemediationTopics.REMEDIATION_EXECUTED, organizationId,
                            new RemediationExecutedEvent(recommendation.getId(), recommendation.getIncidentId(), runbook.getKey(), Instant.now())));
            auditService.record(organizationId, actorUserId, "REMEDIATION_EXECUTED",
                    "RemediationRecommendation", recommendation.getId().toString(), auditMetadataJson);

            recommendation = verificationService.verify(recommendation);
        } else {
            RemediationStateMachine.validateTransition(recommendation.getStatus(), RecommendationStatus.EXECUTION_FAILED);
            recommendation.setStatus(RecommendationStatus.EXECUTION_FAILED);
            recommendation = recommendationRepository.save(recommendation);

            eventPublisher.publish(RemediationTopics.REMEDIATION_EXECUTION_FAILED, recommendation.getId().toString(),
                    EventEnvelope.of(RemediationTopics.REMEDIATION_EXECUTION_FAILED, organizationId,
                            new RemediationExecutionFailedEvent(recommendation.getId(), recommendation.getIncidentId(), result.detail(), Instant.now())));
            auditService.record(organizationId, actorUserId, "REMEDIATION_EXECUTION_FAILED",
                    "RemediationRecommendation", recommendation.getId().toString(), auditMetadataJson);
        }

        return assembler.toResponse(recommendation);
    }

    /** AuditService.metadataJson is stored directly into a jsonb column with no
     *  validation - it must always be valid JSON text or null, never a raw
     *  human-readable string. Wrap plain executor/detail text here so every
     *  audit call site in this class is safe by construction. */
    private String toAuditMetadataJson(String detail) {
        if (detail == null) return null;
        try {
            return objectMapper.writeValueAsString(Map.of("detail", detail));
        } catch (Exception e) {
            log.warn("Failed to serialize audit metadata detail, omitting it: {}", e.getMessage());
            return null;
        }
    }
}