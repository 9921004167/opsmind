package com.opsmind.core.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.alert.dto.AlertIngestRequest;
import com.opsmind.core.incident.Incident;
import com.opsmind.core.incident.IncidentAlertLinkRepository;
import com.opsmind.core.incident.IncidentRepository;
import com.opsmind.core.incident.IncidentSeverity;
import com.opsmind.core.incident.IncidentService;
import com.opsmind.core.incident.IncidentStatus;
import com.opsmind.core.kafka.EventPublisher;
import com.opsmind.core.tenant.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertIngestionServiceTest {

    @Mock AlertRepository alertRepository;
    @Mock ProjectRepository projectRepository;
    @Mock ProjectEnvironmentRepository environmentRepository;
    @Mock MonitoredServiceRepository serviceRepository;
    @Mock IncidentRepository incidentRepository;
    @Mock IncidentAlertLinkRepository incidentAlertLinkRepository;
    @Mock IncidentService incidentService;
    @Mock EventPublisher eventPublisher;

    AlertIngestionService alertIngestionService;

    UUID orgId = UUID.randomUUID();
    UUID projectId = UUID.randomUUID();
    UUID envId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        alertIngestionService = new AlertIngestionService(alertRepository, projectRepository, environmentRepository,
                serviceRepository, incidentRepository, incidentAlertLinkRepository, incidentService, eventPublisher,
                new ObjectMapper());
    }

    private AlertIngestRequest validRequest() {
        return new AlertIngestRequest(projectId, envId, null, "prometheus", "high_error_rate",
                AlertSeverity.HIGH, "Payment Service Elevated 5xx Rate", "5xx rate above threshold",
                "http_5xx_rate", java.math.BigDecimal.valueOf(10), java.math.BigDecimal.valueOf(23.4),
                null, null);
    }

    @Test
    void rejectsAlertForProjectNotOwnedByCallingOrganization() {
        Project foreignProject = Project.builder().id(projectId).organizationId(UUID.randomUUID()).build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(foreignProject));

        assertThatThrownBy(() -> alertIngestionService.ingest(orgId, validRequest()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown projectId");

        verifyNoInteractions(alertRepository, incidentService);
    }

    @Test
    void ingestingAValidAlertPersistsItPublishesAndCreatesExactlyOneIncident() {
        Project project = Project.builder().id(projectId).organizationId(orgId).build();
        ProjectEnvironment env = ProjectEnvironment.builder().id(envId).projectId(projectId).build();
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(environmentRepository.findById(envId)).thenReturn(Optional.of(env));
        when(alertRepository.save(any(Alert.class))).thenAnswer(inv -> {
            Alert a = inv.getArgument(0);
            if (a.getId() == null) a.setId(UUID.randomUUID());
            return a;
        });
        Incident incident = Incident.builder().id(UUID.randomUUID()).incidentNumber("INC-000010")
                .status(IncidentStatus.NEW).severity(IncidentSeverity.HIGH).build();
        when(incidentService.createFromAlert(eq(orgId), eq(projectId), eq(envId), isNull(), anyString(),
                eq(IncidentSeverity.HIGH), any(UUID.class))).thenReturn(incident);

        var response = alertIngestionService.ingest(orgId, validRequest());

        assertThat(response.createdIncidentId()).isEqualTo(incident.getId());
        assertThat(response.createdIncidentNumber()).isEqualTo("INC-000010");
        verify(incidentService, times(1)).createFromAlert(any(), any(), any(), any(), any(), any(), any());
        verify(eventPublisher).publish(eq("alert.received"), anyString(), any());
        verify(alertRepository, times(2)).save(any(Alert.class)); // once RECEIVED, once updated to LINKED
    }
}
