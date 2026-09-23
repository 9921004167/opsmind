package com.opsmind.core.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Real Kafka producer - not a stub. Serialization failures and broker errors are
 * logged loudly rather than swallowed, per the "do not hide failures" rule.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public <T> void publish(String topic, String key, EventEnvelope<T> envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish event {} to topic {}: {}", envelope.eventId(), topic, ex.getMessage(), ex);
                } else {
                    log.info("Published event {} ({}) to topic {} partition {}",
                            envelope.eventId(), envelope.eventType(), topic,
                            result.getRecordMetadata().partition());
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize event {} for topic {}: {}", envelope.eventId(), topic, e.getMessage(), e);
            throw new IllegalStateException("Could not publish event to " + topic, e);
        }
    }
}
