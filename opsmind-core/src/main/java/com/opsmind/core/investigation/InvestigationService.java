package com.opsmind.core.investigation;

import com.opsmind.core.audit.AuditService;
import com.opsmind.core.common.NotFoundException;
import com.opsmind.core.incident.Incident;
import com.opsmind.core.incident.IncidentRepository;
import com.opsmind.core.investigation.ai.AIInvestigationException;
import com.opsmind.core.investigation.ai.AIInvestigator;
import com.opsmind.core.investigation.ai.RcaResult;
import com.opsmind.core.investigation.collector.EvidenceCollectionContext;
import com.opsmind.core.investigation.collector.EvidenceCollector;
import com.opsmind.core.investigation.dto.EvidenceResponse;
import com.opsmind.core.investigation.dto.InvestigationResponse;
import com.opsmind.core.investigation.dto.RcaFindingResponse;
import com.opsmind.core.investigation.event.*;
import com.opsmind.core.kafka.EventEnvelope;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.tenant.MonitoredService;
import com.opsmind.core.tenant.MonitoredServiceRepository;
import com.acme.incident.memory.evidence.HistoricalIncidentEvidence;
import com.acme.incident.memory.evidence.HistoricalIncidentEvidenceProvider;
import com.acme.incident.memory.port.ActiveIncidentView;
import com.acme.incident.memory.port.ResolvedIncidentView;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Orchestrates: fetch incident -> build a bounded evidence-collection context ->
 * run every registered EvidenceCollector (each isolated by try/catch - one
 * collector's failure never blocks the others) -> persist Evidence -> hand
 * everything collected to the AIInvestigator -> persist RcaFinding on success, or
 * mark the investigation FAILED (with the collected Evidence still intact and
 * visible) on any AI failure. This method is intentionally synchronous - Phase 6
 * scope does not include an async job queue; a request to run an investigation
 * blocks until evidence collection + Gemini's response are both done (can take
 * several seconds). That is a known simplification, not an oversight.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InvestigationService {

    private final InvestigationRepository investigationRepository;
    private final EvidenceRepository evidenceRepository;
    private final RcaFindingRepository rcaFindingRepository;
    private final IncidentRepository incidentRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;
    private final List<EvidenceCollector> evidenceCollectors; // Spring injects every EvidenceCollector bean
    private final AIInvestigator aiInvestigator;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;
    private final HistoricalIncidentEvidenceProvider historicalIncidentEvidenceProvider;

    @Transactional
    public InvestigationResponse createAndRun(UUID organizationId, UUID incidentId, UUID actorUserId) {
        Incident incident = incidentRepository.findByIdAndOrganizationId(incidentId, organizationId)
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));

        Investigation investigation = Investigation.builder()
                .incidentId(incident.getId())
                .organizationId(organizationId)
                .status(InvestigationStatus.RUNNING)
                .build();
        investigation = investigationRepository.save(investigation);

        publishAndAudit(investigation, organizationId, actorUserId);

        return runInvestigation(investigation, incident, organizationId, actorUserId);
    }

    @Transactional
    public InvestigationResponse rerun(UUID organizationId, UUID investigationId, UUID actorUserId) {
        Investigation investigation = investigationRepository.findByIdAndOrganizationId(investigationId, organizationId)
                .orElseThrow(() -> new NotFoundException("Investigation not found: " + investigationId));
        if (investigation.getStatus() == InvestigationStatus.RUNNING) {
            throw new com.opsmind.core.common.DomainException("Investigation is already running: " + investigationId);
        }
        UUID incidentId = investigation.getIncidentId();
	Incident incident = incidentRepository.findByIdAndOrganizationId(incidentId, organizationId)
            .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));

        investigation.setStatus(InvestigationStatus.RUNNING);
        investigation.setFailureReason(null);
        investigation.setCompletedAt(null);
        investigation = investigationRepository.save(investigation);

        return runInvestigation(investigation, incident, organizationId, actorUserId);
    }

    private InvestigationResponse runInvestigation(Investigation investigation, Incident incident, UUID organizationId, UUID actorUserId) {
        String serviceSlug = null;
        if (incident.getPrimaryServiceId() != null) {
            serviceSlug = monitoredServiceRepository.findById(incident.getPrimaryServiceId())
                    .map(MonitoredService::getSlug).orElse(null);
        }

        Instant windowEnd = Instant.now();
        Instant windowStart = incident.getCreatedAt().isBefore(windowEnd.minus(Duration.ofMinutes(30)))
                ? windowEnd.minus(Duration.ofMinutes(30))
                : incident.getCreatedAt().minus(Duration.ofMinutes(2));

        EvidenceCollectionContext context = new EvidenceCollectionContext(
                investigation.getId(), organizationId, incident.getProjectId(), incident.getEnvironmentId(),
                incident.getPrimaryServiceId(), serviceSlug, windowStart, windowEnd);

        List<Evidence> collected = new java.util.ArrayList<>();
        for (EvidenceCollector collector : evidenceCollectors) {
            try {
                List<Evidence> result = collector.collect(context);
                collected.addAll(result);
            } catch (Exception e) {
                // Spec requirement: one collector failing must not block the others
                // or fail the whole investigation.
                log.error("Evidence collector '{}' threw unexpectedly (treated as zero evidence from it): {}",
                        collector.name(), e.getMessage(), e);
            }
        }
        List<Evidence> historical = fetchHistoricalEvidence(investigation, incident, serviceSlug, collected);
        collected.addAll(historical);

        collected.forEach(e -> evidenceRepository.save(e));

        AIInvestigator.IncidentSummary summary = new AIInvestigator.IncidentSummary(
                incident.getIncidentNumber(), incident.getTitle(), incident.getSeverity().name(), serviceSlug);

        try {
            RcaResult result = aiInvestigator.investigate(summary, collected);
            RcaFinding finding = persistFinding(investigation, result);

            investigation.setStatus(InvestigationStatus.COMPLETED);
            investigation.setAiProvider(aiInvestigator.providerName());
            investigation.setAiModel(aiInvestigator.modelName());
            investigation.setCompletedAt(Instant.now());
            investigation = investigationRepository.save(investigation);

            eventPublisher.publish(InvestigationTopics.INVESTIGATION_COMPLETED, investigation.getId().toString(),
                    EventEnvelope.of(InvestigationTopics.INVESTIGATION_COMPLETED, organizationId,
                            new InvestigationCompletedEvent(investigation.getId(), incident.getId(), result.confidenceLevel(), investigation.getCompletedAt())));
            auditService.record(organizationId, actorUserId, "INVESTIGATION_COMPLETED", "Investigation", investigation.getId().toString(), null);

            return toResponse(investigation, collected, finding);

        } catch (AIInvestigationException e) {
            investigation.setStatus(InvestigationStatus.FAILED);
            investigation.setFailureReason(e.getMessage());
            investigation.setCompletedAt(Instant.now());
            investigation = investigationRepository.save(investigation);

            log.error("Investigation {} failed: {}", investigation.getId(), e.getMessage());
            eventPublisher.publish(InvestigationTopics.INVESTIGATION_FAILED, investigation.getId().toString(),
                    EventEnvelope.of(InvestigationTopics.INVESTIGATION_FAILED, organizationId,
                            new InvestigationFailedEvent(investigation.getId(), incident.getId(), e.getMessage(), investigation.getCompletedAt())));
            auditService.record(organizationId, actorUserId, "INVESTIGATION_FAILED", "Investigation", investigation.getId().toString(), null);

            return toResponse(investigation, collected, null);
        }
    }
    
        /**
     * Phase 7 integration point. Retrieves similar resolved incidents from
     * organizational memory and converts them into ordinary Evidence rows
     * (type = HISTORICAL_INCIDENT) so they flow through the exact same path
     * as live evidence - no changes needed in AIInvestigator/GeminiAIInvestigator.
     *
     * Memory is an enhancement, never a dependency: any failure here is
     * logged and swallowed, exactly like a failed EvidenceCollector above.
     */
    private List<Evidence> fetchHistoricalEvidence(Investigation investigation, Incident incident,
                                                    String serviceSlug, List<Evidence> liveEvidence) {
        try {
            List<ResolvedIncidentView.Symptom> symptoms = liveEvidence.stream()
                    .map(e -> new ResolvedIncidentView.Symptom(e.getSource(), e.getTitle(), e.getDescription()))
                    .toList();

            List<String> evidenceSummaries = liveEvidence.stream()
                    .map(e -> e.getTitle() + ": " + e.getObservedValue())
                    .toList();

            ActiveIncidentView active = new ActiveIncidentView(
                    incident.getId(),
                    investigation.getOrganizationId(),
                    incident.getTitle(),
                    incident.getSeverity().name(),
                    serviceSlug == null ? List.of() : List.of(serviceSlug),
                    incident.getCreatedAt(),
                    symptoms,
                    evidenceSummaries);

            List<HistoricalIncidentEvidence> matches = historicalIncidentEvidenceProvider.collect(active);

            return matches.stream()
                    .map(h -> toEvidence(investigation.getId(), h))
                    .toList();

        } catch (Exception e) {
            log.warn("Historical incident memory lookup failed for investigation {}; " +
                    "continuing without it: {}", investigation.getId(), e.getMessage(), e);
            return List.of();
        }
    }

    private Evidence toEvidence(UUID investigationId, HistoricalIncidentEvidence h) {
        String metadataJson;
        try {
            metadataJson = objectMapper.writeValueAsString(java.util.Map.of(
                    "historicalIncidentId", h.historicalIncidentId().toString(),
                    "similarity", h.similarity(),
                    "confidence", h.confidence(),
                    "recommendations", h.recommendations()));
        } catch (Exception e) {
            metadataJson = "{}";
        }

        return Evidence.builder()
                .investigationId(investigationId)
                .type(EvidenceType.HISTORICAL_INCIDENT)
                .source("incident-memory")
                .observedAt(h.resolvedAt())
                .title("Similar past incident: " + h.title())
                .description("Root cause: " + h.rootCause() + " | Resolution: " + h.resolution())
                .observedValue(String.format("%.2f similarity", h.similarity()))
                .metadata(metadataJson)
                .build();
    }
    private RcaFinding persistFinding(Investigation investigation, RcaResult result) {
        try {
            String supportingIdsJson = objectMapper.writeValueAsString(result.supportingEvidenceIds());
            String actionsJson = objectMapper.writeValueAsString(result.recommendedActions());
            RcaFinding finding = RcaFinding.builder()
                    .investigationId(investigation.getId())
                    .rootCause(result.rootCause())
                    .hypothesis(result.hypothesis())
                    .confidenceLevel(RcaConfidence.valueOf(result.confidenceLevel()))
                    .confidenceScore(result.confidenceScore())
                    .affectedService(result.affectedService())
                    .impact(result.impact())
                    .supportingEvidenceIds(supportingIdsJson)
                    .reasoningSummary(result.reasoningSummary())
                    .recommendedActions(actionsJson)
                    .modelProvider(aiInvestigator.providerName())
                    .modelName(aiInvestigator.modelName())
                    .build();
            return rcaFindingRepository.save(finding);
        } catch (Exception e) {
            // Serialization of an already-validated result should not fail, but if
            // it does, treat it the same as any other AI-stage failure rather than
            // letting a RuntimeException escape uncaught.
            throw new AIInvestigationException("failed_to_persist_rca: " + e.getMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public List<InvestigationResponse> listForIncident(UUID organizationId, UUID incidentId) {
        incidentRepository.findByIdAndOrganizationId(incidentId, organizationId)
                .orElseThrow(() -> new NotFoundException("Incident not found: " + incidentId));
        return investigationRepository.findByIncidentIdOrderByCreatedAtDesc(incidentId).stream()
                .map(inv -> toResponse(inv, evidenceRepository.findByInvestigationIdOrderByObservedAtAsc(inv.getId()),
                        rcaFindingRepository.findByInvestigationId(inv.getId()).orElse(null)))
                .toList();
    }

    @Transactional(readOnly = true)
    public InvestigationResponse get(UUID organizationId, UUID investigationId) {
        Investigation investigation = investigationRepository.findByIdAndOrganizationId(investigationId, organizationId)
                .orElseThrow(() -> new NotFoundException("Investigation not found: " + investigationId));
        List<Evidence> evidence = evidenceRepository.findByInvestigationIdOrderByObservedAtAsc(investigation.getId());
        RcaFinding finding = rcaFindingRepository.findByInvestigationId(investigation.getId()).orElse(null);
        return toResponse(investigation, evidence, finding);
    }

    private void publishAndAudit(Investigation investigation, UUID organizationId, UUID actorUserId) {
        eventPublisher.publish(InvestigationTopics.INVESTIGATION_STARTED, investigation.getId().toString(),
                EventEnvelope.of(InvestigationTopics.INVESTIGATION_STARTED, organizationId,
                        new InvestigationStartedEvent(investigation.getId(), investigation.getIncidentId(), investigation.getStartedAt())));
        auditService.record(organizationId, actorUserId, "INVESTIGATION_STARTED", "Investigation", investigation.getId().toString(), null);
    }

    private InvestigationResponse toResponse(Investigation investigation, List<Evidence> evidence, RcaFinding finding) {
        List<EvidenceResponse> evidenceResponses = evidence.stream()
                .map(e -> new EvidenceResponse(e.getId(), e.getType(), e.getSource(), e.getObservedAt(), e.getTitle(), e.getDescription(), e.getObservedValue()))
                .toList();

        RcaFindingResponse findingResponse = null;
        if (finding != null) {
            List<UUID> supportingIds;
            List<String> actions;
            try {
                supportingIds = List.of(objectMapper.readValue(finding.getSupportingEvidenceIds(), UUID[].class));
                actions = List.of(objectMapper.readValue(finding.getRecommendedActions(), String[].class));
            } catch (Exception e) {
                supportingIds = List.of();
                actions = List.of();
            }
            findingResponse = new RcaFindingResponse(finding.getId(), finding.getRootCause(), finding.getHypothesis(),
                    finding.getConfidenceLevel().name(), finding.getConfidenceScore(), finding.getAffectedService(),
                    finding.getImpact(), supportingIds, finding.getReasoningSummary(), actions,
                    finding.getModelProvider(), finding.getModelName(), finding.getGeneratedAt());
        }

        return new InvestigationResponse(investigation.getId(), investigation.getIncidentId(), investigation.getStatus(),
                investigation.getFailureReason(), investigation.getAiProvider(), investigation.getAiModel(),
                investigation.getStartedAt(), investigation.getCompletedAt(), evidenceResponses, findingResponse);
    }
}
