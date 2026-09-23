package com.opsmind.core.incident;

import com.opsmind.core.common.DomainException;
import com.opsmind.core.common.IncidentNumberGenerator;
import com.opsmind.core.common.NotFoundException;
import com.opsmind.core.incident.dto.IncidentStatusUpdateRequest;
import com.opsmind.core.kafka.EventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IncidentServiceTest {

@Mock
IncidentRepository incidentRepository;

@Mock
IncidentEventRepository incidentEventRepository;

@Mock
IncidentAlertLinkRepository incidentAlertLinkRepository;

@Mock
IncidentNumberGenerator incidentNumberGenerator;

@Mock
EventPublisher eventPublisher;

@Mock
ApplicationEventPublisher applicationEventPublisher;

IncidentService incidentService;

UUID orgId = UUID.randomUUID();
UUID projectId = UUID.randomUUID();
UUID envId = UUID.randomUUID();
UUID alertId = UUID.randomUUID();

@BeforeEach
void setUp() {
    incidentService = new IncidentService(
            incidentRepository,
            incidentEventRepository,
            incidentAlertLinkRepository,
            incidentNumberGenerator,
            eventPublisher,
            applicationEventPublisher
    );
}

@Test
void createFromAlertPersistsIncidentAtNewStatusAndLinksTheAlert() {
    when(incidentNumberGenerator.next()).thenReturn("INC-000001");

    when(incidentRepository.save(any(Incident.class))).thenAnswer(inv -> {
        Incident i = inv.getArgument(0);
        i.setId(UUID.randomUUID());
        return i;
    });

    Incident result = incidentService.createFromAlert(
            orgId,
            projectId,
            envId,
            null,
            "Payment Service Elevated 5xx Rate",
            IncidentSeverity.HIGH,
            alertId
    );

    assertThat(result.getStatus()).isEqualTo(IncidentStatus.NEW);
    assertThat(result.getIncidentNumber()).isEqualTo("INC-000001");
    assertThat(result.getSeverity()).isEqualTo(IncidentSeverity.HIGH);

    verify(incidentAlertLinkRepository).save(argThat(link ->
            link.getAlertId().equals(alertId)
                    && link.getCorrelationReason().equals("naive_one_to_one")
    ));

    verify(incidentEventRepository, times(2)).save(any(IncidentEvent.class));

    verify(eventPublisher).publish(
            eq("incident.created"),
            anyString(),
            any()
    );
}

@Test
void updateStatusRejectsIllegalTransitionAndDoesNotPublishOrPersist() {
    Incident incident = Incident.builder()
            .id(UUID.randomUUID())
            .organizationId(orgId)
            .status(IncidentStatus.NEW)
            .incidentNumber("INC-000002")
            .build();

    when(incidentRepository.findByIdAndOrganizationId(
            incident.getId(),
            orgId
    )).thenReturn(Optional.of(incident));

    assertThatThrownBy(() ->
            incidentService.updateStatus(
                    orgId,
                    incident.getId(),
                    new IncidentStatusUpdateRequest(
                            IncidentStatus.RESOLVED,
                            null
                    ),
                    "user-1"
            )
    ).isInstanceOf(DomainException.class);

    verify(incidentRepository, never()).save(any());

    verify(eventPublisher, never()).publish(
            anyString(),
            anyString(),
            any()
    );
}

@Test
void updateStatusAppliesLegalTransitionAndRecordsTimelineAndEvent() {
    Incident incident = Incident.builder()
            .id(UUID.randomUUID())
            .organizationId(orgId)
            .status(IncidentStatus.NEW)
            .incidentNumber("INC-000003")
            .build();

    when(incidentRepository.findByIdAndOrganizationId(
            incident.getId(),
            orgId
    )).thenReturn(Optional.of(incident));

    when(incidentRepository.save(any(Incident.class)))
            .thenAnswer(inv -> inv.getArgument(0));

    var response = incidentService.updateStatus(
            orgId,
            incident.getId(),
            new IncidentStatusUpdateRequest(
                    IncidentStatus.INVESTIGATING,
                    "starting investigation"
            ),
            "user-1"
    );

    assertThat(response.status())
            .isEqualTo(IncidentStatus.INVESTIGATING);

    ArgumentCaptor<IncidentEvent> captor =
            ArgumentCaptor.forClass(IncidentEvent.class);

    verify(incidentEventRepository).save(captor.capture());

    assertThat(captor.getValue().getDescription())
            .contains("NEW -> INVESTIGATING")
            .contains("starting investigation");

    verify(eventPublisher).publish(
            eq("incident.status.changed"),
            anyString(),
            any()
    );
}

@Test
void getIncidentThrowsNotFoundWhenIncidentBelongsToAnotherOrganization() {
    UUID incidentId = UUID.randomUUID();

    when(incidentRepository.findByIdAndOrganizationId(
            incidentId,
            orgId
    )).thenReturn(Optional.empty());

    assertThatThrownBy(() ->
            incidentService.getIncident(orgId, incidentId)
    ).isInstanceOf(NotFoundException.class);
}

}
