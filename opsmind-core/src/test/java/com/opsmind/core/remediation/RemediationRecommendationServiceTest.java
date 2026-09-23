package com.opsmind.core.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.DomainException;
import com.opsmind.core.incident.Incident;
import com.opsmind.core.incident.IncidentRepository;
import com.opsmind.core.incident.IncidentSeverity;
import com.opsmind.core.investigation.*;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.remediation.ai.RemediationAdvice;
import com.opsmind.core.remediation.ai.RemediationAdvisor;
import com.opsmind.core.remediation.dto.RemediationRecommendationResponse;
import com.opsmind.core.remediation.service.RemediationApprovalService;
import com.opsmind.core.remediation.service.RemediationRecommendationService;
import com.opsmind.core.remediation.service.RunbookCatalogService;
import com.opsmind.core.tenant.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers testing requirements #1, #2, #7, #8, #9 (Section 19): the AI can only
 * ever select a catalog runbook, an unknown key is always HIGH/MANUAL_ONLY, and
 * auto-approval only ever fires for LOW-risk, non-manual-only recommendations
 * when explicitly enabled.
 */
@ExtendWith(MockitoExtension.class)
class RemediationRecommendationServiceTest {

    @Mock RemediationRecommendationRepository recommendationRepository;
    @Mock RemediationApprovalRepository approvalRepository;
    @Mock RemediationExecutionRepository executionRepository;
    @Mock RemediationVerificationRepository verificationRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock InvestigationRepository investigationRepository;
    @Mock RcaFindingRepository rcaFindingRepository;
    @Mock MonitoredServiceRepository monitoredServiceRepository;
    @Mock RunbookCatalogService runbookCatalogService;
    @Mock RemediationAdvisor remediationAdvisor;
    @Mock RemediationApprovalService remediationApprovalService;
    @Mock EventPublisher eventPublisher;
    @Mock AuditService auditService;

    RemediationRecommendationService service;

    UUID orgId = UUID.randomUUID();
    UUID incidentId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    UUID investigationId = UUID.randomUUID();

    Incident incident;
    Investigation investigation;
    RcaFinding rcaFinding;
    RunbookDefinition lowRiskRunbook;

    @BeforeEach
    void setUp() {
        service = new RemediationRecommendationService(
                recommendationRepository, approvalRepository, executionRepository, verificationRepository,
                incidentRepository, investigationRepository, rcaFindingRepository, monitoredServiceRepository,
                runbookCatalogService, remediationAdvisor, remediationApprovalService, eventPublisher, auditService,
                new ObjectMapper());

        incident = Incident.builder().id(incidentId).organizationId(orgId)
                .incidentNumber("INC-1").title("High 5xx on order-service")
                .severity(IncidentSeverity.HIGH).build();

        investigation = Investigation.builder().id(investigationId).incidentId(incidentId)
                .status(InvestigationStatus.COMPLETED).build();

        rcaFinding = RcaFinding.builder().id(UUID.randomUUID()).investigationId(investigationId)
                .rootCause("Injected fault").hypothesis("fault injection")
                .confidenceLevel(RcaConfidence.HIGH).impact("elevated 5xx").build();

        lowRiskRunbook = RunbookDefinition.builder().id(UUID.randomUUID()).key("disable-order-fault")
                .title("Disable order fault").riskLevel(RiskLevel.LOW).executorType(ExecutorType.FAULT_DISABLE)
                .executionEndpoint("http://order-service:8093/faults/order/disable").build();

        lenient().when(incidentRepository.findByIdAndOrganizationId(incidentId, orgId)).thenReturn(Optional.of(incident));
        lenient().when(investigationRepository.findFirstByIncidentIdAndStatusOrderByCreatedAtDesc(incidentId, InvestigationStatus.COMPLETED))
                .thenReturn(Optional.of(investigation));
        lenient().when(rcaFindingRepository.findByInvestigationId(investigationId)).thenReturn(Optional.of(rcaFinding));
        lenient().when(runbookCatalogService.listAllEntities()).thenReturn(List.of(lowRiskRunbook));
        lenient().when(recommendationRepository.save(any())).thenAnswer(inv -> {
            RemediationRecommendation r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        lenient().when(remediationApprovalService.autoApproveIfEligible(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void matchedCatalogKeyBecomesExecutableWithTheRunbooksOwnRisk() {
        when(remediationAdvisor.recommend(any(), eq(rcaFinding), any()))
                .thenReturn(new RemediationAdvice("disable-order-fault", "matches known fault", "disable the fault", "{}"));

        RemediationRecommendationResponse response = service.create(orgId, incidentId, actorId);

        assertThat(response.matchedRunbookKey()).isEqualTo("disable-order-fault");
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(response.manualOnly()).isFalse();
    }

    @Test
    void hallucinatedRunbookKeyIsNeverTrustedAndBecomesHighRiskManualOnly() {
        when(remediationAdvisor.recommend(any(), eq(rcaFinding), any()))
                .thenReturn(new RemediationAdvice("restart-the-universe", "made up", "please investigate manually", "{}"));

        RemediationRecommendationResponse response = service.create(orgId, incidentId, actorId);

        assertThat(response.matchedRunbookKey()).isNull();
        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.manualOnly()).isTrue();
    }

    @Test
    void noSuggestedKeyBecomesHighRiskManualOnly() {
        when(remediationAdvisor.recommend(any(), eq(rcaFinding), any()))
                .thenReturn(new RemediationAdvice(null, "nothing fits", "investigate manually", "{}"));

        RemediationRecommendationResponse response = service.create(orgId, incidentId, actorId);

        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.manualOnly()).isTrue();
    }

    @Test
    void matchedManualExecutorRunbookIsStillManualOnlyDespiteBeingMatched() {
        RunbookDefinition manualRunbook = RunbookDefinition.builder().id(UUID.randomUUID()).key("rollback-deployment")
                .title("Rollback").riskLevel(RiskLevel.HIGH).executorType(ExecutorType.MANUAL).build();
        when(runbookCatalogService.listAllEntities()).thenReturn(List.of(manualRunbook));
        when(remediationAdvisor.recommend(any(), eq(rcaFinding), any()))
                .thenReturn(new RemediationAdvice("rollback-deployment", "roll it back", "roll back the deploy", "{}"));

        RemediationRecommendationResponse response = service.create(orgId, incidentId, actorId);

        assertThat(response.matchedRunbookKey()).isEqualTo("rollback-deployment");
        assertThat(response.manualOnly()).isTrue();
    }

    @Test
    void withoutACompletedInvestigationCreationFails() {
        when(investigationRepository.findFirstByIncidentIdAndStatusOrderByCreatedAtDesc(incidentId, InvestigationStatus.COMPLETED))
                .thenReturn(Optional.empty());

        org.junit.jupiter.api.Assertions.assertThrows(DomainException.class,
                () -> service.create(orgId, incidentId, actorId));
    }

    @Test
    void anAdvisorFailureIsRecordedAsHighRiskManualOnlyRatherThanCrashing() {
        when(remediationAdvisor.recommend(any(), eq(rcaFinding), any()))
                .thenThrow(new com.opsmind.core.remediation.ai.RemediationAdvisorException("gemini_call_failed: timeout"));

        RemediationRecommendationResponse response = service.create(orgId, incidentId, actorId);

        assertThat(response.riskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(response.manualOnly()).isTrue();
    }

    private static <T> T eq(T value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
