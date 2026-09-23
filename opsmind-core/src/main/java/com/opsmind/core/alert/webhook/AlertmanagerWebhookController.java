package com.opsmind.core.alert.webhook;

import com.opsmind.core.alert.AlertIngestionService;
import com.opsmind.core.alert.AlertSeverity;
import com.opsmind.core.alert.dto.AlertIngestRequest;
import com.opsmind.core.tenant.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * Adapter endpoint: Prometheus fires an alert -> Alertmanager groups/routes it ->
 * Alertmanager POSTs its own webhook JSON shape here -> this translates each firing
 * alert into OpsMind's generic AlertIngestRequest and reuses the SAME ingestion path
 * (AlertIngestionService) that a human-triggered curl to /api/alerts would use. This
 * is deliberate: there is exactly one code path that turns an alert into an
 * incident, regardless of where the alert came from (Section 6's "pluggable
 * providers" requirement).
 *
 * Resolution handling (status="resolved") is NOT implemented in Phase 4 - closing
 * the loop automatically when a metric recovers is Phase 12 (verification) work.
 * Resolved notifications are logged and otherwise ignored, not silently dropped
 * without a trace.
 */
@RestController
@RequestMapping("/api/alerts/webhooks")
@RequiredArgsConstructor
@Slf4j
public class AlertmanagerWebhookController {

    private final AlertIngestionService alertIngestionService;
    private final OrganizationRepository organizationRepository;
    private final ProjectRepository projectRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;

    @Value("${opsmind.security.webhook.token}")
    private String webhookToken;

    @PostMapping("/alertmanager")
    public ResponseEntity<?> receive(@RequestParam String token, @RequestBody AlertmanagerWebhookPayload payload) {
        if (webhookToken == null || webhookToken.isBlank() || !webhookToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "FORBIDDEN", "message", "Invalid or missing webhook token"));
        }

        int created = 0;
        int skipped = 0;
        for (AlertmanagerWebhookPayload.AlertmanagerAlert alert : payload.alerts()) {
            if (!"firing".equalsIgnoreCase(alert.status())) {
                log.info("Ignoring non-firing Alertmanager notification (status={}) for {}", alert.status(), alert.labels());
                skipped++;
                continue;
            }
            try {
                ingestOne(alert);
                created++;
            } catch (WebhookMappingException e) {
                // Deliberately does not throw a 500 for the whole batch - one
                // unmapped alert should not block the rest from being ingested.
                log.error("Could not map Alertmanager alert to an OpsMind tenant: {}. Labels: {}", e.getMessage(), alert.labels());
                skipped++;
            }
        }
        return ResponseEntity.ok(Map.of("received", payload.alerts().size(), "created", created, "skipped", skipped));
    }

    private void ingestOne(AlertmanagerWebhookPayload.AlertmanagerAlert alert) {
        Map<String, String> labels = alert.labels();
        Map<String, String> annotations = alert.annotations();

        String orgSlug = require(labels, "organization");
        String projectSlug = require(labels, "project");
        String environmentType = require(labels, "environment");
        String serviceSlug = labels.get("service"); // optional - some alerts may not map to a single service

        Organization org = organizationRepository.findBySlug(orgSlug)
                .orElseThrow(() -> new WebhookMappingException("Unknown organization slug: " + orgSlug));
        Project project = projectRepository.findByOrganizationIdAndSlug(org.getId(), projectSlug)
                .orElseThrow(() -> new WebhookMappingException("Unknown project slug: " + projectSlug + " in org " + orgSlug));
        EnvironmentType envType;
        try {
            envType = EnvironmentType.valueOf(environmentType);
        } catch (IllegalArgumentException e) {
            throw new WebhookMappingException("Unknown environment type: " + environmentType);
        }
        ProjectEnvironment env = environmentRepository.findByProjectIdAndType(project.getId(), envType)
                .orElseThrow(() -> new WebhookMappingException("No " + environmentType + " environment registered for project " + projectSlug));

        java.util.UUID serviceId = null;
        if (serviceSlug != null) {
            serviceId = monitoredServiceRepository.findByProjectIdAndSlug(project.getId(), serviceSlug)
                    .map(MonitoredService::getId)
                    .orElse(null); // unknown service slug -> alert still ingested, just unattributed to a service
        }

        AlertSeverity severity = parseSeverity(labels.get("severity"));
        String title = annotations.getOrDefault("summary", labels.getOrDefault("alertname", "Prometheus alert"));
        String description = annotations.get("description");
        String alertType = labels.getOrDefault("alert_type", labels.getOrDefault("alertname", "prometheus_alert"));

        Instant startedAt;
        try {
            startedAt = Instant.parse(alert.startsAt());
        } catch (Exception e) {
            startedAt = Instant.now();
        }

        AlertIngestRequest request = new AlertIngestRequest(
                project.getId(), env.getId(), serviceId,
                "prometheus", alertType, severity, title, description,
                null, null, null, startedAt, Map.of());

        alertIngestionService.ingest(org.getId(), request);
    }

    private AlertSeverity parseSeverity(String raw) {
        if (raw == null) return AlertSeverity.MEDIUM;
        try {
            return AlertSeverity.valueOf(raw.toUpperCase());
        } catch (IllegalArgumentException e) {
            return AlertSeverity.MEDIUM;
        }
    }

    private String require(Map<String, String> labels, String key) {
        String value = labels.get(key);
        if (value == null || value.isBlank()) {
            throw new WebhookMappingException("Missing required label: " + key);
        }
        return value;
    }
}
