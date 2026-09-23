package com.opsmind.core.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.DomainException;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.remediation.dto.RemediationRecommendationResponse;
import com.opsmind.core.remediation.executor.RemediationExecutor;
import com.opsmind.core.remediation.service.RemediationExecutionService;
import com.opsmind.core.remediation.service.RemediationVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers testing requirements #3, #4, #11 (Section 19): a manual_only
 * recommendation can never become executable, execution is blocked unless
 * APPROVED, and a failed execution is persisted (not silently discarded).
 *
 * Note: verify(executor, never()).execute(any()) is used instead of
 * verifyNoInteractions(executor) - the service constructor legitimately calls
 * executor.type() once while building its internal executorsByType map
 * (Collectors.toMap(RemediationExecutor::type, ...)), so "zero interactions
 * ever" is too strict a check; what actually matters is that execute() itself
 * was never invoked.
 */
@ExtendWith(MockitoExtension.class)
class RemediationExecutionServiceTest {

    @Mock RemediationRecommendationRepository recommendationRepository;
    @Mock RemediationExecutionRepository executionRepository;
    @Mock RunbookDefinitionRepository runbookDefinitionRepository;
    @Mock RemediationVerificationService verificationService;
    @Mock EventPublisher eventPublisher;
    @Mock AuditService auditService;

    RemediationExecutionService service;

    UUID orgId = UUID.randomUUID();
    UUID recId = UUID.randomUUID();
    UUID incidentId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID runbookId = UUID.randomUUID();

    RunbookDefinition faultRunbook;

    @BeforeEach
    void setUp() {
        faultRunbook = RunbookDefinition.builder().id(runbookId).key("disable-order-fault")
                .title("Disable order fault").riskLevel(RiskLevel.LOW).executorType(ExecutorType.FAULT_DISABLE)
                .executionEndpoint("http://order-service:8093/faults/order/disable").build();

        lenient().when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private RemediationRecommendation approvedRecommendation(boolean manualOnly, UUID runbookIdOrNull) {
        return RemediationRecommendation.builder()
                .id(recId).incidentId(incidentId).organizationId(orgId)
                .riskLevel(RiskLevel.LOW).manualOnly(manualOnly).runbookId(runbookIdOrNull)
                .status(RecommendationStatus.APPROVED).build();
    }

    private RemediationExecutionService serviceWithExecutor(RemediationExecutor executor) {
        return new RemediationExecutionService(recommendationRepository, executionRepository,
               runbookDefinitionRepository, verificationService, eventPublisher, auditService,
                new ObjectMapper(), List.of(executor));
    }

    @Test
    void manualOnlyRecommendationCanNeverBeExecuted() {
        RemediationRecommendation rec = approvedRecommendation(true, null);
        when(recommendationRepository.findByIdAndOrganizationId(recId, orgId)).thenReturn(Optional.of(rec));
        RemediationExecutor executor = mock(RemediationExecutor.class);
        service = serviceWithExecutor(executor);

        assertThatThrownBy(() -> service.execute(orgId, recId, actorId, r -> null))
                .isInstanceOf(DomainException.class)
                .hasMessageContaining("manual_only");

        verify(executor, never()).execute(any());
        verify(executionRepository, never()).save(any());
    }

    @Test
    void executionIsBlockedUnlessApproved() {
        RemediationRecommendation rec = approvedRecommendation(false, runbookId);
        rec.setStatus(RecommendationStatus.PROPOSED); // not yet approved
        when(recommendationRepository.findByIdAndOrganizationId(recId, orgId)).thenReturn(Optional.of(rec));
        RemediationExecutor executor = mock(RemediationExecutor.class);
        service = serviceWithExecutor(executor);

        assertThatThrownBy(() -> service.execute(orgId, recId, actorId, r -> null))
                .isInstanceOf(DomainException.class);

        verify(executor, never()).execute(any());
    }

    @Test
    void successfulExecutionTransitionsToExecutedAndTriggersVerification() {
        RemediationRecommendation rec = approvedRecommendation(false, runbookId);
        when(recommendationRepository.findByIdAndOrganizationId(recId, orgId)).thenReturn(Optional.of(rec));
        when(runbookDefinitionRepository.findById(runbookId)).thenReturn(Optional.of(faultRunbook));

        RemediationExecutor executor = mock(RemediationExecutor.class);
        when(executor.type()).thenReturn(ExecutorType.FAULT_DISABLE);
        when(executor.execute(faultRunbook)).thenReturn(new RemediationExecutor.ExecutionResult(true, "ok"));
        when(verificationService.verify(any())).thenAnswer(inv -> {
            RemediationRecommendation r = inv.getArgument(0);
            r.setStatus(RecommendationStatus.VERIFIED_RECOVERED);
            return r;
        });

        service = serviceWithExecutor(executor);
        RemediationRecommendationResponse response = service.execute(orgId, recId, actorId,
                r -> new RemediationRecommendationResponse(r.getId(), r.getIncidentId(), null, null, null, null, null,
                        r.getRiskLevel(), r.isManualOnly(), r.getStatus(), null, null, List.of(), List.of(), List.of()));

        ArgumentCaptor<RemediationExecution> captor = ArgumentCaptor.forClass(RemediationExecution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().isSucceeded()).isTrue();
        verify(verificationService).verify(any());
        assertThat(response.status()).isEqualTo(RecommendationStatus.VERIFIED_RECOVERED);
    }

    @Test
    void failedExecutionIsPersistedAndVerificationIsNeverCalled() {
        RemediationRecommendation rec = approvedRecommendation(false, runbookId);
        when(recommendationRepository.findByIdAndOrganizationId(recId, orgId)).thenReturn(Optional.of(rec));
        when(runbookDefinitionRepository.findById(runbookId)).thenReturn(Optional.of(faultRunbook));

        RemediationExecutor executor = mock(RemediationExecutor.class);
        when(executor.type()).thenReturn(ExecutorType.FAULT_DISABLE);
        when(executor.execute(faultRunbook)).thenReturn(new RemediationExecutor.ExecutionResult(false, "connection refused"));

        service = serviceWithExecutor(executor);
        service.execute(orgId, recId, actorId, r -> null);

        ArgumentCaptor<RemediationExecution> captor = ArgumentCaptor.forClass(RemediationExecution.class);
        verify(executionRepository).save(captor.capture());
        assertThat(captor.getValue().isSucceeded()).isFalse();
        assertThat(captor.getValue().getDetail()).contains("connection refused");
        verifyNoInteractions(verificationService);
        assertThat(rec.getStatus()).isEqualTo(RecommendationStatus.EXECUTION_FAILED);
    }
}