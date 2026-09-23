package com.opsmind.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.investigation.InvestigationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes INCIDENT_CREATED on its own consumer group (separate from
 * PlatformEventListener's audit-only group) and automatically starts an AI
 * investigation for every new incident.
 *
 * This closes a real gap: previously, "AI Investigation" (Phase 6) only ever ran
 * when a human or the frontend explicitly called POST .../investigations. An
 * incident created purely from an automatic Alertmanager webhook would otherwise
 * sit with zero evidence and zero RCA until someone noticed and triggered it by
 * hand.
 *
 * actorUserId is null here: investigations triggered by this listener are
 * system-initiated, not by a logged-in user. InvestigationService/AuditService
 * must tolerate a null actor for this to work - see the audit trail entry this
 * produces ("system" rather than a user id).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AutoInvestigationListener {

    private final InvestigationService investigationService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.INCIDENT_CREATED, groupId = "opsmind-core-auto-investigation")
    public void onIncidentCreated(String message) {
        UUID organizationId;
        UUID incidentId;

        try {
            JsonNode root = objectMapper.readTree(message);
            organizationId = UUID.fromString(root.get("organizationId").asText());
            incidentId = UUID.fromString(root.path("payload").get("incidentId").asText());
        } catch (Exception e) {
            log.error("Could not parse INCIDENT_CREATED event for auto-investigation: {}", e.getMessage(), e);
            return;
        }

        try {
            log.info("Auto-starting investigation for incident {} (org {})", incidentId, organizationId);
            investigationService.createAndRun(organizationId, incidentId, null);
        } catch (Exception e) {
            // Never let an auto-investigation failure take down the Kafka
            // consumer or block other incidents from being processed - same
            // philosophy as every other "AI stage" failure in this codebase
            // (AIInvestigator, RemediationAdvisor): log it, move on, the
            // incident remains visible and a human can retry manually.
            log.error("Auto-investigation failed for incident {}: {}", incidentId, e.getMessage(), e);
        }
    }
}
