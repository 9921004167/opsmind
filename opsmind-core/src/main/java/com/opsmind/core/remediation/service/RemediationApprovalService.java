package com.opsmind.core.remediation.service;

import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.DomainException;
import com.opsmind.core.common.NotFoundException;
import com.opsmind.core.kafka.EventEnvelope;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.remediation.*;
import com.opsmind.core.remediation.dto.RemediationRecommendationResponse;
import com.opsmind.core.remediation.event.RemediationApprovedEvent;
import com.opsmind.core.remediation.event.RemediationRejectedEvent;
import com.opsmind.core.remediation.event.RemediationTopics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Human approve/reject (Section 15) plus auto-approval eligibility (Section 11).
 * Auto-approval is OFF by default (opsmind.remediation.auto-approve-low-risk=false).
 * Even when auto-approved, a real RemediationApproval row is always created with
 * autoApproved=true and actorUserId=null, so the audit trail never has a gap.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemediationApprovalService {

    private final RemediationRecommendationRepository recommendationRepository;
    private final RemediationApprovalRepository approvalRepository;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;

    @Value("${opsmind.remediation.auto-approve-low-risk:false}")
    private boolean autoApproveLowRisk;

    /** Called once, right after a recommendation is created. Never called again -
     *  a recommendation that missed auto-approval eligibility at creation time
     *  always requires a human decision afterwards. */
    @Transactional
    public RemediationRecommendation autoApproveIfEligible(RemediationRecommendation recommendation) {
        if (!autoApproveLowRisk) return recommendation;
        if (recommendation.isManualOnly()) return recommendation;
        if (recommendation.getRiskLevel() != RiskLevel.LOW) return recommendation;
        if (recommendation.getStatus() != RecommendationStatus.PROPOSED) return recommendation;

        RemediationStateMachine.validateTransition(recommendation.getStatus(), RecommendationStatus.APPROVED);

        RemediationApproval approval = RemediationApproval.builder()
                .recommendationId(recommendation.getId())
                .organizationId(recommendation.getOrganizationId())
                .decision(ApprovalDecision.APPROVED)
                .actorUserId(null)
                .autoApproved(true)
                .reason("auto-approved: LOW risk, opsmind.remediation.auto-approve-low-risk=true")
                .build();
        approvalRepository.save(approval);

        recommendation.setStatus(RecommendationStatus.APPROVED);
        recommendation = recommendationRepository.save(recommendation);

        publishApproved(recommendation, true);
        auditService.record(recommendation.getOrganizationId(), null, "REMEDIATION_AUTO_APPROVED",
                "RemediationRecommendation", recommendation.getId().toString(), null);

        return recommendation;
    }

    @Transactional
    public RemediationRecommendationResponse approve(UUID organizationId, UUID recommendationId, UUID actorUserId, String reason, RecommendationResponseAssembler assembler) {
        RemediationRecommendation recommendation = load(organizationId, recommendationId);
        RemediationStateMachine.validateTransition(recommendation.getStatus(), RecommendationStatus.APPROVED);

        RemediationApproval approval = RemediationApproval.builder()
                .recommendationId(recommendation.getId())
                .organizationId(organizationId)
                .decision(ApprovalDecision.APPROVED)
                .actorUserId(actorUserId)
                .autoApproved(false)
                .reason(reason)
                .build();
        approvalRepository.save(approval);

        recommendation.setStatus(RecommendationStatus.APPROVED);
        recommendation = recommendationRepository.save(recommendation);

        publishApproved(recommendation, false);
        auditService.record(organizationId, actorUserId, "REMEDIATION_APPROVED",
                "RemediationRecommendation", recommendation.getId().toString(), null);

        return assembler.toResponse(recommendation);
    }

    @Transactional
    public RemediationRecommendationResponse reject(UUID organizationId, UUID recommendationId, UUID actorUserId, String reason, RecommendationResponseAssembler assembler) {
        RemediationRecommendation recommendation = load(organizationId, recommendationId);
        RemediationStateMachine.validateTransition(recommendation.getStatus(), RecommendationStatus.REJECTED);

        RemediationApproval approval = RemediationApproval.builder()
                .recommendationId(recommendation.getId())
                .organizationId(organizationId)
                .decision(ApprovalDecision.REJECTED)
                .actorUserId(actorUserId)
                .autoApproved(false)
                .reason(reason)
                .build();
        approvalRepository.save(approval);

        recommendation.setStatus(RecommendationStatus.REJECTED);
        recommendation = recommendationRepository.save(recommendation);

        eventPublisher.publish(RemediationTopics.REMEDIATION_REJECTED, recommendation.getId().toString(),
                EventEnvelope.of(RemediationTopics.REMEDIATION_REJECTED, organizationId,
                        new RemediationRejectedEvent(recommendation.getId(), recommendation.getIncidentId(), reason, Instant.now())));
        auditService.record(organizationId, actorUserId, "REMEDIATION_REJECTED",
                "RemediationRecommendation", recommendation.getId().toString(), null);

        return assembler.toResponse(recommendation);
    }

    private void publishApproved(RemediationRecommendation recommendation, boolean autoApproved) {
        eventPublisher.publish(RemediationTopics.REMEDIATION_APPROVED, recommendation.getId().toString(),
                EventEnvelope.of(RemediationTopics.REMEDIATION_APPROVED, recommendation.getOrganizationId(),
                        new RemediationApprovedEvent(recommendation.getId(), recommendation.getIncidentId(), autoApproved, Instant.now())));
    }

    private RemediationRecommendation load(UUID organizationId, UUID id) {
        return recommendationRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("Remediation recommendation not found: " + id));
    }
}
