package com.opsmind.core.remediation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.audit.AuditService;
import com.opsmind.core.incident.Incident;
import com.opsmind.core.incident.IncidentRepository;
import com.opsmind.core.investigation.collector.PrometheusEvidenceCollector;
import com.opsmind.core.kafka.EventEnvelope;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.remediation.*;
import com.opsmind.core.remediation.event.RemediationTopics;
import com.opsmind.core.remediation.event.RemediationVerifiedEvent;
import com.opsmind.core.tenant.MonitoredService;
import com.opsmind.core.tenant.MonitoredServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

/**
 * Checks whether an executed remediation actually worked, reusing
 * PrometheusEvidenceCollector.query5xxRate - the SAME real query path Phase 6
 * uses for evidence collection (Section 17). No query logic is duplicated here.
 *
 * KNOWN SIMPLIFICATION (documented, not hidden): this runs synchronously,
 * immediately after execution, checking a short trailing window. A real 5xx-rate
 * drop can take longer to show up in Prometheus than that window allows, so a
 * genuinely-recovered service can still come back NOT_RECOVERED/INCONCLUSIVE
 * here. There is no background re-check job yet - see Investigation's own
 * documented limitations for the same kind of "real but incomplete" caveat.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RemediationVerificationService {

    private final RemediationRecommendationRepository recommendationRepository;
    private final RemediationVerificationRepository verificationRepository;
    private final IncidentRepository incidentRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;
    private final PrometheusEvidenceCollector prometheusEvidenceCollector;
    private final EventPublisher eventPublisher;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Value("${opsmind.remediation.verification.window-seconds:120}")
    private long verificationWindowSeconds;

    @Value("${opsmind.remediation.verification.max-5xx-rate:0.05}")
    private double max5xxRate;

    @Transactional
    public RemediationRecommendation verify(RemediationRecommendation recommendation) {
        RemediationStateMachine.validateTransition(recommendation.getStatus(), RecommendationStatus.VERIFYING);
        recommendation.setStatus(RecommendationStatus.VERIFYING);
        recommendation = recommendationRepository.save(recommendation);

        Incident incident = incidentRepository.findById(recommendation.getIncidentId()).orElse(null);
        String serviceSlug = resolveServiceSlug(incident);

        VerificationResult result;
        String observedValue;
        String detail;
        String metricName = "http_5xx_rate";

        if (serviceSlug == null) {
            result = VerificationResult.INCONCLUSIVE;
            observedValue = null;
            detail = "no_primary_service_on_incident: cannot resolve a service slug to query";
        } else {
            try {
                Optional<Double> rate = prometheusEvidenceCollector.query5xxRate(serviceSlug, Duration.ofSeconds(verificationWindowSeconds));
                if (rate.isEmpty()) {
                    result = VerificationResult.INCONCLUSIVE;
                    observedValue = null;
                    detail = "no_prometheus_data_in_window for service " + serviceSlug;
                } else if (rate.get() < max5xxRate) {
                    result = VerificationResult.RECOVERED;
                    observedValue = String.valueOf(rate.get());
                    detail = "5xx rate " + rate.get() + " is below threshold " + max5xxRate;
                } else {
                    result = VerificationResult.NOT_RECOVERED;
                    observedValue = String.valueOf(rate.get());
                    detail = "5xx rate " + rate.get() + " is still at/above threshold " + max5xxRate;
                }
            } catch (Exception e) {
                log.error("Verification Prometheus query failed for recommendation {}: {}", recommendation.getId(), e.getMessage(), e);
                result = VerificationResult.INCONCLUSIVE;
                observedValue = null;
                detail = "prometheus_query_failed: " + e.getMessage();
            }
        }

        RemediationVerification verification = RemediationVerification.builder()
                .recommendationId(recommendation.getId())
                .organizationId(recommendation.getOrganizationId())
                .result(result)
                .metricName(metricName)
                .observedValue(observedValue)
                .detail(detail)
                .build();
        verificationRepository.save(verification);

        RecommendationStatus finalStatus = result == VerificationResult.RECOVERED
                ? RecommendationStatus.VERIFIED_RECOVERED : RecommendationStatus.VERIFICATION_FAILED;
        RemediationStateMachine.validateTransition(recommendation.getStatus(), finalStatus);
        recommendation.setStatus(finalStatus);
        recommendation = recommendationRepository.save(recommendation);

        eventPublisher.publish(RemediationTopics.REMEDIATION_VERIFIED, recommendation.getId().toString(),
                EventEnvelope.of(RemediationTopics.REMEDIATION_VERIFIED, recommendation.getOrganizationId(),
                        new RemediationVerifiedEvent(recommendation.getId(), recommendation.getIncidentId(), result.name(), Instant.now())));
        auditService.record(recommendation.getOrganizationId(), null, "REMEDIATION_VERIFIED",
                "RemediationRecommendation", recommendation.getId().toString(), toAuditMetadataJson(detail));

        return recommendation;
    }

    private String resolveServiceSlug(Incident incident) {
        if (incident == null || incident.getPrimaryServiceId() == null) return null;
        return monitoredServiceRepository.findById(incident.getPrimaryServiceId())
                .map(MonitoredService::getSlug).orElse(null);
    }

    /**
     * AuditService.metadataJson maps straight to a JSONB column with no
     * validation on the way in - it must always be valid JSON text or null,
     * never a raw human-readable string. detail here is free text (e.g.
     * "no_prometheus_data_in_window for service payment-service"), so it must
     * be wrapped before reaching AuditService, exactly like
     * RemediationExecutionService already does for its own detail strings.
     */
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