package com.opsmind.core.remediation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.DomainException;
import com.opsmind.core.common.NotFoundException;
import com.opsmind.core.incident.Incident;
import com.opsmind.core.incident.IncidentRepository;
import com.opsmind.core.investigation.Investigation;
import com.opsmind.core.investigation.InvestigationRepository;
import com.opsmind.core.investigation.InvestigationStatus;
import com.opsmind.core.investigation.RcaFinding;
import com.opsmind.core.investigation.RcaFindingRepository;
import com.opsmind.core.kafka.EventEnvelope;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.remediation.*;
import com.opsmind.core.remediation.ai.RemediationAdvice;
import com.opsmind.core.remediation.ai.RemediationAdvisor;
import com.opsmind.core.remediation.ai.RemediationAdvisorException;
import com.opsmind.core.remediation.dto.*;
import com.opsmind.core.remediation.event.RemediationRecommendedEvent;
import com.opsmind.core.remediation.event.RemediationTopics;
import com.opsmind.core.tenant.MonitoredService;
import com.opsmind.core.tenant.MonitoredServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Orchestrates: load incident + its latest completed Investigation's RcaFinding ->
 * load the closed runbook catalog -> ask RemediationAdvisor -> validate the
 * suggested key against the REAL catalog (never trust the AI's own bookkeeping,
 * Section 18) -> persist a RemediationRecommendation -> hand off to
 * RemediationApprovalService for auto-approval eligibility (Section 11).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemediationRecommendationService {

    private final RemediationRecommendationRepository recommendationRepository;
    private final RemediationApprovalRepository approvalRepository;
    private final RemediationExecutionRepository executionRepository;
    private final RemediationVerificationRepository verificationRepository;
    private final IncidentRepository incidentRepository;
    private final InvestigationRepository investigationRepository;
    private final RcaFindingRepository rcaFindingRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;
    private final RunbookCatalogService runbookCatalogService;
    private final RemediationAdvisor remediationAdvisor;
    private final RemediationApprovalService remediationApprovalService;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional
    public RemediationRecommendationResponse create(UUID organizationId, UUID incidentId, UUID actorUserId) {
        Incident incident = incidentRepository.findByIdAndOrganizationId(incidentId, organizationId)
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));

        Investigation investigation = investigationRepository
                .findFirstByIncidentIdAndStatusOrderByCreatedAtDesc(incidentId, InvestigationStatus.COMPLETED)
                .orElseThrow(() -> new DomainException(
                        "no_completed_investigation: incident " + incidentId + " has no completed investigation with an RCA finding yet"));

        RcaFinding rcaFinding = rcaFindingRepository.findByInvestigationId(investigation.getId())
                .orElseThrow(() -> new DomainException("no_rca_finding: investigation " + investigation.getId() + " has no RCA finding"));

        String serviceSlug = null;
        if (incident.getPrimaryServiceId() != null) {
            serviceSlug = monitoredServiceRepository.findById(incident.getPrimaryServiceId())
                    .map(MonitoredService::getSlug).orElse(null);
        }

        List<RunbookDefinition> catalog = runbookCatalogService.listAllEntities();

        RemediationAdvisor.IncidentSummary summary = new RemediationAdvisor.IncidentSummary(
                incident.getIncidentNumber(), incident.getTitle(), incident.getSeverity().name(), serviceSlug);

        RemediationAdvice advice;
        try {
            advice = remediationAdvisor.recommend(summary, rcaFinding, catalog);
        } catch (RemediationAdvisorException e) {
            // Same philosophy as AIInvestigator failures (Phase 6): never crash the
            // incident flow. An advisor failure is treated exactly like an unmatched
            // runbook key - HIGH risk, MANUAL_ONLY, safe by construction.
            log.warn("Remediation advisor failed for incident {}; recording as MANUAL_ONLY/HIGH: {}", incidentId, e.getMessage());
            advice = new RemediationAdvice(null, "advisor_unavailable: " + e.getMessage(),
                    "AI remediation advice is unavailable (" + e.getMessage() + "). A human must review this incident manually.", null);
        }

        RunbookDefinition matched = null;
        String suggestedKey = advice.suggestedRunbookKey();
        if (suggestedKey != null) {
            matched = catalog.stream()
                    .filter(r -> r.getKey().equals(suggestedKey))
                    .findFirst()
                    .orElse(null);
            if (matched == null) {
                log.warn("Gemini suggested unknown/hallucinated runbook key '{}' for incident {} - treating as HIGH/MANUAL_ONLY",
                        advice.suggestedRunbookKey(), incidentId);
            }
        }

        RiskLevel riskLevel = matched != null ? matched.getRiskLevel() : RiskLevel.HIGH;
        boolean manualOnly = matched == null || matched.getExecutorType() == ExecutorType.MANUAL;

        RemediationRecommendation recommendation = RemediationRecommendation.builder()
                .incidentId(incidentId)
                .organizationId(organizationId)
                .rcaFindingId(rcaFinding.getId())
                .runbookId(matched != null ? matched.getId() : null)
                .matchedRunbookKey(matched != null ? matched.getKey() : null)
                .aiSuggestedKey(advice.suggestedRunbookKey())
                .aiRecommendationText(advice.freeTextRecommendation())
                .aiRationale(advice.rationale())
                .aiRawResponse(advice.rawJson())
                .riskLevel(riskLevel)
                .manualOnly(manualOnly)
                .status(RecommendationStatus.PROPOSED)
                .createdByUserId(actorUserId)
                .build();
        recommendation = recommendationRepository.save(recommendation);

        eventPublisher.publish(RemediationTopics.REMEDIATION_RECOMMENDED, recommendation.getId().toString(),
                EventEnvelope.of(RemediationTopics.REMEDIATION_RECOMMENDED, organizationId,
                        new RemediationRecommendedEvent(recommendation.getId(), incidentId, riskLevel.name(), manualOnly, recommendation.getCreatedAt())));
        auditService.record(organizationId, actorUserId, "REMEDIATION_RECOMMENDED", "RemediationRecommendation",
                recommendation.getId().toString(), safeMetadata(riskLevel, manualOnly, matched));

        recommendation = remediationApprovalService.autoApproveIfEligible(recommendation);

        return toResponse(recommendation);
    }

    @Transactional(readOnly = true)
    public List<RemediationRecommendationResponse> listForIncident(UUID organizationId, UUID incidentId) {
        incidentRepository.findByIdAndOrganizationId(incidentId, organizationId)
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));
        return recommendationRepository.findByIncidentIdAndOrganizationIdOrderByCreatedAtDesc(incidentId, organizationId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RemediationRecommendationResponse get(UUID organizationId, UUID id) {
        RemediationRecommendation recommendation = recommendationRepository.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(() -> new NotFoundException("Remediation recommendation not found: " + id));
        return toResponse(recommendation);
    }

    private String safeMetadata(RiskLevel riskLevel, boolean manualOnly, RunbookDefinition matched) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "riskLevel", riskLevel.name(),
                    "manualOnly", manualOnly,
                    "matchedRunbookKey", matched != null ? matched.getKey() : "none"));
        } catch (Exception e) {
            return null;
        }
    }

    public RemediationRecommendationResponse toResponse(RemediationRecommendation r) {
        List<RemediationApprovalResponse> approvals = approvalRepository.findByRecommendationIdOrderByCreatedAtDesc(r.getId()).stream()
                .map(a -> new RemediationApprovalResponse(a.getId(), a.getDecision(), a.getActorUserId(), a.isAutoApproved(), a.getReason(), a.getCreatedAt()))
                .toList();
        List<RemediationExecutionResponse> executions = executionRepository.findByRecommendationIdOrderByStartedAtDesc(r.getId()).stream()
                .map(e -> new RemediationExecutionResponse(e.getId(), e.getExecutorType(), e.isSucceeded(), e.getDetail(), e.getActorUserId(), e.getStartedAt(), e.getCompletedAt()))
                .toList();
        List<RemediationVerificationResponse> verifications = verificationRepository.findByRecommendationIdOrderByVerifiedAtDesc(r.getId()).stream()
                .map(v -> new RemediationVerificationResponse(v.getId(), v.getResult(), v.getMetricName(), v.getObservedValue(), v.getDetail(), v.getVerifiedAt()))
                .toList();

        return new RemediationRecommendationResponse(r.getId(), r.getIncidentId(), r.getRcaFindingId(),
                r.getMatchedRunbookKey(), r.getAiSuggestedKey(), r.getAiRecommendationText(), r.getAiRationale(),
                r.getRiskLevel(), r.isManualOnly(), r.getStatus(), r.getCreatedAt(), r.getUpdatedAt(),
                approvals, executions, verifications);
    }
}
