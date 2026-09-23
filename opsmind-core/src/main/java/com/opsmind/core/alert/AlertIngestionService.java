package com.opsmind.core.alert;

import com.opsmind.core.alert.dto.AlertIngestRequest;
import com.opsmind.core.alert.dto.AlertResponse;
import com.opsmind.core.alert.event.AlertReceivedEvent;
import com.opsmind.core.incident.Incident;
import com.opsmind.core.incident.IncidentAlertLink;
import com.opsmind.core.incident.IncidentAlertLinkRepository;
import com.opsmind.core.incident.IncidentRepository;
import com.opsmind.core.incident.IncidentSeverity;
import com.opsmind.core.incident.IncidentService;
import com.opsmind.core.kafka.EventEnvelope;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.kafka.KafkaTopics;
import com.opsmind.core.tenant.MonitoredServiceRepository;
import com.opsmind.core.tenant.ProjectEnvironmentRepository;
import com.opsmind.core.tenant.ProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The front door for machine-generated alerts (Section 6/7).
 *
 * Correlation (added post-Phase-1): before creating a new incident, checks
 * whether an open incident already exists for the same
 * org+project+environment+service, raised by the same alert type. If so, this
 * alert is treated as the same ongoing issue repeating - e.g. Alertmanager's
 * repeat_interval re-sending a still-firing alert every minute - and gets
 * linked to the existing incident instead of spawning a duplicate incident
 * and a duplicate AI investigation. A service having two genuinely different
 * concurrent problems (e.g. high 5xx AND high latency at once) still
 * correctly gets two incidents, since alert_type must match, not just
 * service.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AlertIngestionService {

    private final AlertRepository alertRepository;
    private final ProjectRepository projectRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final MonitoredServiceRepository serviceRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentAlertLinkRepository incidentAlertLinkRepository;
    private final IncidentService incidentService;
    private final EventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    public AlertResponse ingest(UUID organizationId, AlertIngestRequest request) {
        validateTenantOwnership(organizationId, request);

        String rawPayloadJson;
        try {
            rawPayloadJson = objectMapper.writeValueAsString(request);
        } catch (Exception e) {
            throw new IllegalArgumentException("Alert payload could not be serialized: " + e.getMessage());
        }

        Alert alert = Alert.builder()
                .organizationId(organizationId)
                .projectId(request.projectId())
                .environmentId(request.environmentId())
                .serviceId(request.serviceId())
                .source(request.source())
                .alertType(request.alertType())
                .severity(request.severity())
                .title(request.title())
                .description(request.description())
                .metricName(request.metricName())
                .thresholdValue(request.thresholdValue())
                .currentValue(request.currentValue())
                .rawPayload(rawPayloadJson)
                .receivedAt(request.timestamp() != null ? request.timestamp() : Instant.now())
                .build();
        alert = alertRepository.save(alert);

        eventPublisher.publish(KafkaTopics.ALERT_RECEIVED, alert.getId().toString(),
                EventEnvelope.of(KafkaTopics.ALERT_RECEIVED, organizationId,
                        new AlertReceivedEvent(alert.getId(), alert.getProjectId(), alert.getEnvironmentId(),
                                alert.getServiceId(), alert.getSource(), alert.getAlertType(), alert.getSeverity(),
                                alert.getTitle(), alert.getReceivedAt())));

        UUID alertId = alert.getId();
        UUID alertProjectId = alert.getProjectId();
        UUID alertEnvironmentId = alert.getEnvironmentId();
        UUID alertServiceId = alert.getServiceId();
        String alertTitle = alert.getTitle();
        IncidentSeverity alertSeverity = toIncidentSeverity(alert.getSeverity());

        Incident incident = findCorrelatedIncident(organizationId, alert)
                .map(existing -> {
                    incidentAlertLinkRepository.save(
                            IncidentAlertLink.builder()
                                    .incidentId(existing.getId())
                                    .alertId(alertId)
                                    .correlationReason("same_service_and_alert_type_open_incident")
                                    .build());
                    return existing;
                })
                .orElseGet(() -> incidentService.createFromAlert(
                        organizationId, alertProjectId, alertEnvironmentId, alertServiceId,
                        alertTitle, alertSeverity, alertId));

        alert.setStatus(AlertStatus.LINKED);
        alert = alertRepository.save(alert);

        return toResponse(alert, incident);
    }

    private Optional<Incident> findCorrelatedIncident(UUID organizationId, Alert alert) {
        if (alert.getServiceId() == null) {
            return Optional.empty(); // nothing to correlate on without a service
        }

        List<Incident> openCandidates = incidentRepository.findOpenByServiceForCorrelation(
                organizationId, alert.getProjectId(), alert.getEnvironmentId(), alert.getServiceId());

        for (Incident candidate : openCandidates) {
            boolean sameAlertType = incidentAlertLinkRepository.findByIncidentId(candidate.getId()).stream()
                    .map(link -> alertRepository.findById(link.getAlertId()))
                    .flatMap(Optional::stream)
                    .anyMatch(linkedAlert -> linkedAlert.getAlertType().equals(alert.getAlertType()));

            if (sameAlertType) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private void validateTenantOwnership(UUID organizationId, AlertIngestRequest request) {
        var project = projectRepository.findById(request.projectId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown projectId: " + request.projectId()));
        if (!project.getOrganizationId().equals(organizationId)) {
            throw new IllegalArgumentException("Unknown projectId: " + request.projectId());
        }
        var environment = environmentRepository.findById(request.environmentId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown environmentId: " + request.environmentId()));
        if (!environment.getProjectId().equals(project.getId())) {
            throw new IllegalArgumentException("environmentId does not belong to projectId");
        }
        if (request.serviceId() != null) {
            var service = serviceRepository.findById(request.serviceId())
                    .orElseThrow(() -> new IllegalArgumentException("Unknown serviceId: " + request.serviceId()));
            if (!service.getProjectId().equals(project.getId())) {
                throw new IllegalArgumentException("serviceId does not belong to projectId");
            }
        }
    }

    private IncidentSeverity toIncidentSeverity(AlertSeverity severity) {
        return IncidentSeverity.valueOf(severity.name());
    }

    private AlertResponse toResponse(Alert alert, Incident incident) {
        return new AlertResponse(alert.getId(), alert.getProjectId(), alert.getEnvironmentId(), alert.getServiceId(),
                alert.getSource(), alert.getAlertType(), alert.getSeverity(), alert.getStatus(), alert.getTitle(),
                alert.getDescription(), alert.getMetricName(), alert.getThresholdValue(), alert.getCurrentValue(),
                alert.getReceivedAt(), incident.getId(), incident.getIncidentNumber());
    }
}