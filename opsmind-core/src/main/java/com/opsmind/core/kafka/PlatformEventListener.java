package com.opsmind.core.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.audit.AuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes the events this same application publishes and writes them to the audit
 * log. This exists to prove the Kafka producer -> broker -> consumer loop genuinely
 * works end-to-end in Phase 1 (Section 22/26: OpsMind's own event pipeline must be
 * observable, not just assumed to work because a send() call didn't throw).
 *
 * Idempotency: since eventId is unique per publish and this listener only appends an
 * audit row (not a mutation with side effects), duplicate delivery on redelivery/retry
 * results in duplicate audit rows rather than duplicate incidents/alerts - acceptable
 * for Phase 1. Exactly-once dedup keyed on eventId is a Phase 6+ concern once
 * consumers perform state-changing side effects.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformEventListener {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @KafkaListener(topics = KafkaTopics.ALERT_RECEIVED, groupId = "opsmind-core-audit")
    public void onAlertReceived(String message) {
        consumeAndAudit(message, "ALERT_RECEIVED_EVENT_CONSUMED", "Alert");
    }

    @KafkaListener(topics = KafkaTopics.INCIDENT_CREATED, groupId = "opsmind-core-audit")
    public void onIncidentCreated(String message) {
        consumeAndAudit(message, "INCIDENT_CREATED_EVENT_CONSUMED", "Incident");
    }

    @KafkaListener(topics = KafkaTopics.INCIDENT_STATUS_CHANGED, groupId = "opsmind-core-audit")
    public void onIncidentStatusChanged(String message) {
        consumeAndAudit(message, "INCIDENT_STATUS_CHANGED_EVENT_CONSUMED", "Incident");
    }

    private void consumeAndAudit(String message, String action, String entityType) {
        try {
            JsonNode root = objectMapper.readTree(message);
            UUID organizationId = UUID.fromString(root.get("organizationId").asText());
            String eventId = root.get("eventId").asText();
            log.info("Consumed {} eventId={}", action, eventId);
            auditService.recordSystemAction(organizationId, "kafka-consumer", action, entityType, eventId, message);
        } catch (Exception e) {
            log.error("Failed to process platform event, action={}, message={}: {}", action, message, e.getMessage(), e);
        }
    }
}
