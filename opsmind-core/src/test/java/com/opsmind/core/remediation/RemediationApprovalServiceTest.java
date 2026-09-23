package com.opsmind.core.remediation;

import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.DomainException;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.remediation.service.RemediationApprovalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Covers testing requirements #7, #8, #9, #13 (Section 19): auto-approval is
 * off by default, only ever auto-approves LOW-risk/non-manual-only work when
 * explicitly enabled, and illegal transitions (e.g. approving twice) are rejected.
 */
@ExtendWith(MockitoExtension.class)
class RemediationApprovalServiceTest {

    @Mock RemediationRecommendationRepository recommendationRepository;
    @Mock RemediationApprovalRepository approvalRepository;
    @Mock EventPublisher eventPublisher;
    @Mock AuditService auditService;

    RemediationApprovalService service;

    UUID orgId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RemediationApprovalService(recommendationRepository, approvalRepository, eventPublisher, auditService);
        // lenient: not every test below actually reaches a save() call (e.g. the
        // ones that short-circuit before persisting anything), so this stub is
        // legitimately unused in some tests rather than a sign of dead test code.
        lenient().when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void setAutoApprove(boolean value) throws Exception {
        Field f = RemediationApprovalService.class.getDeclaredField("autoApproveLowRisk");
        f.setAccessible(true);
        f.set(service, value);
    }

    private RemediationRecommendation newRecommendation(RiskLevel risk, boolean manualOnly, RecommendationStatus status) {
        return RemediationRecommendation.builder().id(UUID.randomUUID()).organizationId(orgId)
                .incidentId(UUID.randomUUID()).riskLevel(risk).manualOnly(manualOnly).status(status).build();
    }

    @Test
    void autoApprovalIsOffByDefault() {
        RemediationRecommendation rec = newRecommendation(RiskLevel.LOW, false, RecommendationStatus.PROPOSED);
        RemediationRecommendation result = service.autoApproveIfEligible(rec);
        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.PROPOSED);
        verifyNoInteractions(approvalRepository);
    }

    @Test
    void enablingAutoApprovalOnlyAffectsLowRiskNonManualOnly() throws Exception {
        setAutoApprove(true);

        RemediationRecommendation low = newRecommendation(RiskLevel.LOW, false, RecommendationStatus.PROPOSED);
        RemediationRecommendation result = service.autoApproveIfEligible(low);
        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.APPROVED);

        ArgumentCaptor<RemediationApproval> captor = ArgumentCaptor.forClass(RemediationApproval.class);
        verify(approvalRepository).save(captor.capture());
        assertThat(captor.getValue().isAutoApproved()).isTrue();
        assertThat(captor.getValue().getActorUserId()).isNull();
    }

    @Test
    void mediumAndHighRiskNeverAutoApproveEvenWhenEnabled() throws Exception {
        setAutoApprove(true);

        RemediationRecommendation medium = newRecommendation(RiskLevel.MEDIUM, false, RecommendationStatus.PROPOSED);
        assertThat(service.autoApproveIfEligible(medium).getStatus()).isEqualTo(RecommendationStatus.PROPOSED);

        RemediationRecommendation high = newRecommendation(RiskLevel.HIGH, false, RecommendationStatus.PROPOSED);
        assertThat(service.autoApproveIfEligible(high).getStatus()).isEqualTo(RecommendationStatus.PROPOSED);

        verifyNoInteractions(approvalRepository);
    }

    @Test
    void manualOnlyNeverAutoApprovesEvenIfLowRisk() throws Exception {
        setAutoApprove(true);
        RemediationRecommendation rec = newRecommendation(RiskLevel.LOW, true, RecommendationStatus.PROPOSED);
        assertThat(service.autoApproveIfEligible(rec).getStatus()).isEqualTo(RecommendationStatus.PROPOSED);
        verifyNoInteractions(approvalRepository);
    }

    @Test
    void approvingAnAlreadyApprovedRecommendationIsRejected() {
        RemediationRecommendation rec = newRecommendation(RiskLevel.LOW, false, RecommendationStatus.APPROVED);
        when(recommendationRepository.findByIdAndOrganizationId(rec.getId(), orgId)).thenReturn(Optional.of(rec));

        assertThatThrownBy(() -> service.approve(orgId, rec.getId(), actorId, null, r -> null))
                .isInstanceOf(DomainException.class);
    }

    @Test
    void rejectingFromProposedWorks() {
        RemediationRecommendation rec = newRecommendation(RiskLevel.HIGH, true, RecommendationStatus.PROPOSED);
        when(recommendationRepository.findByIdAndOrganizationId(rec.getId(), orgId)).thenReturn(Optional.of(rec));

        service.reject(orgId, rec.getId(), actorId, "not needed", r -> null);

        assertThat(rec.getStatus()).isEqualTo(RecommendationStatus.REJECTED);
        ArgumentCaptor<RemediationApproval> captor = ArgumentCaptor.forClass(RemediationApproval.class);
        verify(approvalRepository).save(captor.capture());
        assertThat(captor.getValue().getDecision()).isEqualTo(ApprovalDecision.REJECTED);
        assertThat(captor.getValue().getActorUserId()).isEqualTo(actorId);
    }
}