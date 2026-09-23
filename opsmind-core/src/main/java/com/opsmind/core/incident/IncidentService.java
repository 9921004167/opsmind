package com.opsmind.core.incident;

import com.acme.incident.memory.ingest.IncidentResolvedEvent;
import com.opsmind.core.common.IncidentNumberGenerator;
import com.opsmind.core.common.NotFoundException;
import com.opsmind.core.incident.dto.*;
import com.opsmind.core.incident.event.IncidentCreatedEvent;
import com.opsmind.core.incident.event.IncidentStatusChangedEvent;
import com.opsmind.core.kafka.EventEnvelope;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.kafka.KafkaTopics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class IncidentService {

    private final IncidentRepository incidentRepository;
    private final IncidentEventRepository incidentEventRepository;
    private final IncidentAlertLinkRepository incidentAlertLinkRepository;
    private final IncidentNumberGenerator incidentNumberGenerator;
    private final EventPublisher eventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Creates a new incident directly from a single alert
     * (naive 1:1 mapping).
     */
    public Incident createFromAlert(UUID organizationId, UUID projectId, UUID environmentId,
                                    UUID serviceId, String title, IncidentSeverity severity,
                                    UUID sourceAlertId) {

        Incident incident = Incident.builder()
                .incidentNumber(incidentNumberGenerator.next())
                .organizationId(organizationId)
                .projectId(projectId)
                .environmentId(environmentId)
                .primaryServiceId(serviceId)
                .title(title)
                .severity(severity)
                .status(IncidentStatus.NEW)
                .build();

        incident = incidentRepository.save(incident);

        appendTimelineEvent(
                incident.getId(),
                IncidentEventType.INCIDENT_CREATED,
                "Incident created from alert " + sourceAlertId,
                "system",
                null
        );

        incidentAlertLinkRepository.save(
                IncidentAlertLink.builder()
                        .incidentId(incident.getId())
                        .alertId(sourceAlertId)
                        .correlationReason("naive_one_to_one")
                        .build()
        );

        appendTimelineEvent(
                incident.getId(),
                IncidentEventType.ALERT_LINKED,
                "Alert " + sourceAlertId + " linked to incident",
                "system",
                null
        );

        eventPublisher.publish(
                KafkaTopics.INCIDENT_CREATED,
                incident.getId().toString(),
                EventEnvelope.of(
                        KafkaTopics.INCIDENT_CREATED,
                        organizationId,
                        new IncidentCreatedEvent(
                                incident.getId(),
                                incident.getIncidentNumber(),
                                projectId,
                                environmentId,
                                serviceId,
                                title,
                                severity,
                                sourceAlertId,
                                incident.getCreatedAt()
                        )
                )
        );

        return incident;
    }

    @Transactional(readOnly = true)
    public List<IncidentResponse> listIncidents(UUID organizationId) {
        return incidentRepository
                .findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public IncidentResponse getIncident(UUID organizationId, UUID incidentId) {
        return toResponse(requireIncident(organizationId, incidentId));
    }

    @Transactional(readOnly = true)
    public List<IncidentTimelineEntryResponse> getTimeline(
            UUID organizationId,
            UUID incidentId) {

        Incident incident = requireIncident(organizationId, incidentId);

        return incidentEventRepository
                .findByIncidentIdOrderByOccurredAtAsc(incident.getId())
                .stream()
                .map(e -> new IncidentTimelineEntryResponse(
                        e.getId(),
                        e.getEventType(),
                        e.getDescription(),
                        e.getActor(),
                        e.getOccurredAt()
                ))
                .toList();
    }

    public IncidentResponse updateStatus(
            UUID organizationId,
            UUID incidentId,
            IncidentStatusUpdateRequest request,
            String actor) {

        Incident incident = requireIncident(organizationId, incidentId);

        IncidentStatus previous = incident.getStatus();

        IncidentStateMachine.validateTransition(
                previous,
                request.status()
        );

        incident.setStatus(request.status());

        if (request.status() == IncidentStatus.RESOLVED) {
            incident.setResolvedAt(java.time.Instant.now());
        }

        if (request.status() == IncidentStatus.CLOSED) {
            incident.setClosedAt(java.time.Instant.now());
        }

        incident = incidentRepository.save(incident);

        String description =
                "Status changed "
                        + previous
                        + " -> "
                        + request.status()
                        + (request.note() != null
                        ? " (" + request.note() + ")"
                        : "");

        appendTimelineEvent(
                incident.getId(),
                IncidentEventType.STATUS_CHANGED,
                description,
                actor,
                null
        );

        eventPublisher.publish(
                KafkaTopics.INCIDENT_STATUS_CHANGED,
                incident.getId().toString(),
                EventEnvelope.of(
                        KafkaTopics.INCIDENT_STATUS_CHANGED,
                        organizationId,
                        new IncidentStatusChangedEvent(
                                incident.getId(),
                                incident.getIncidentNumber(),
                                previous,
                                request.status(),
                                incident.getUpdatedAt()
                        )
                )
        );

        /*
         * Phase 7:
         * When an incident is resolved, publish a Spring application event.
         * IncidentMemoryEnqueuer listens for this event AFTER_COMMIT
         * and places the incident into the incident_memory queue.
         */
        if (request.status() == IncidentStatus.RESOLVED) {
            applicationEventPublisher.publishEvent(
                    new IncidentResolvedEvent(
                            organizationId,
                            incident.getId()
                    )
            );
        }

        return toResponse(incident);
    }

    private void appendTimelineEvent(
            UUID incidentId,
            IncidentEventType type,
            String description,
            String actor,
            String metadataJson) {

        incidentEventRepository.save(
                IncidentEvent.builder()
                        .incidentId(incidentId)
                        .eventType(type)
                        .description(description)
                        .actor(actor)
                        .metadata(metadataJson)
                        .build()
        );
    }

    private Incident requireIncident(
            UUID organizationId,
            UUID incidentId) {

        return incidentRepository
                .findByIdAndOrganizationId(incidentId, organizationId)
                .orElseThrow(() ->
                        new NotFoundException(
                                "Incident not found: " + incidentId
                        )
                );
    }

    private IncidentResponse toResponse(Incident i) {

        return new IncidentResponse(
                i.getId(),
                i.getIncidentNumber(),
                i.getProjectId(),
                i.getEnvironmentId(),
                i.getPrimaryServiceId(),
                i.getTitle(),
                i.getSeverity(),
                i.getStatus(),
                i.getSummary(),
                i.getCreatedAt(),
                i.getUpdatedAt(),
                i.getResolvedAt(),
                i.getClosedAt(),
                IncidentStateMachine.allowedNext(i.getStatus())
        );
    }
}
