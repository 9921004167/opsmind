package com.opsmind.core.remediation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.audit.AuditService;
import com.opsmind.core.incident.Incident;
import com.opsmind.core.incident.IncidentRepository;
import com.opsmind.core.investigation.collector.PrometheusEvidenceCollector;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.remediation.service.RemediationVerificationService;
import com.opsmind.core.tenant.MonitoredService;
import com.opsmind.core.tenant.MonitoredServiceRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Covers testing requirement #12 (Section 19): verification reuses
 * PrometheusEvidenceCollector's real query path rather than a duplicated one.
 *
 * max5xxRate is set here via reflection because @Value fields are only ever
 * populated by Spring's property resolution machinery, which does not run in
 * a plain Mockito unit test (this class is instantiated directly with `new`,
 * never through a Spring ApplicationContext). Left unset, the field silently
 * keeps Java's primitive double default of 0.0 rather than the real
 * application default of 0.05 - production code is unaffected since Spring
 * always injects the real value there; only this test needs the workaround.
 */
@ExtendWith(MockitoExtension.class)
class RemediationVerificationServiceTest {

    @Mock RemediationRecommendationRepository recommendationRepository;
    @Mock RemediationVerificationRepository verificationRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock MonitoredServiceRepository monitoredServiceRepository;
    @Mock PrometheusEvidenceCollector prometheusEvidenceCollector;
    @Mock EventPublisher eventPublisher;
    @Mock AuditService auditService;

    RemediationVerificationService service;

    UUID orgId = UUID.randomUUID();
    UUID incidentId = UUID.randomUUID();
    UUID serviceId = UUID.randomUUID();

    @BeforeEach
    void setUp() throws Exception {
        service = new RemediationVerificationService(recommendationRepository, verificationRepository,
                incidentRepository, monitoredServiceRepository, prometheusEvidenceCollector, eventPublisher,
                auditService, new ObjectMapper());
        when(recommendationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        setPrivateField("max5xxRate", 0.05);
        setPrivateField("verificationWindowSeconds", 120L);

        Incident incident = Incident.builder().id(incidentId).organizationId(orgId).primaryServiceId(serviceId).build();
        when(incidentRepository.findById(incidentId)).thenReturn(Optional.of(incident));

        MonitoredService svc = MonitoredService.builder().id(serviceId).slug("order-service").build();
        when(monitoredServiceRepository.findById(serviceId)).thenReturn(Optional.of(svc));
    }

    private void setPrivateField(String name, Object value) throws Exception {
        Field f = RemediationVerificationService.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(service, value);
    }

    private RemediationRecommendation executedRecommendation() {
        return RemediationRecommendation.builder().id(UUID.randomUUID()).organizationId(orgId)
                .incidentId(incidentId).riskLevel(RiskLevel.LOW).manualOnly(false)
                .status(RecommendationStatus.EXECUTED).build();
    }

    @Test
    void recoveredWhenFiveXxRateIsBelowThreshold() {
        when(prometheusEvidenceCollector.query5xxRate(eq("order-service"), any(Duration.class)))
                .thenReturn(Optional.of(0.001));

        RemediationRecommendation result = service.verify(executedRecommendation());

        verify(prometheusEvidenceCollector).query5xxRate(eq("order-service"), any(Duration.class));
        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.VERIFIED_RECOVERED);
    }

    @Test
    void notRecoveredWhenFiveXxRateStaysHigh() {
        when(prometheusEvidenceCollector.query5xxRate(eq("order-service"), any(Duration.class)))
                .thenReturn(Optional.of(0.9));

        RemediationRecommendation result = service.verify(executedRecommendation());

        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.VERIFICATION_FAILED);
    }

    @Test
    void inconclusiveWhenNoPrometheusDataIsAvailable() {
        when(prometheusEvidenceCollector.query5xxRate(eq("order-service"), any(Duration.class)))
                .thenReturn(Optional.empty());

        RemediationRecommendation result = service.verify(executedRecommendation());

        assertThat(result.getStatus()).isEqualTo(RecommendationStatus.VERIFICATION_FAILED);
    }
}