package com.opsmind.ecommerce.order.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public <T> void publish(String topic, String key, OrderEventEnvelope<T> envelope) {
        try {
            String json = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish {} to {}: {}", envelope.eventType(), topic, ex.getMessage(), ex);
                } else {
                    log.info("Published {} to {}", envelope.eventType(), topic);
                }
            });
        } catch (Exception e) {
            log.error("Failed to serialize event for topic {}: {}", topic, e.getMessage(), e);
            // Deliberately NOT rethrown: a Kafka publish failure should not fail the
            // customer's order if payment+inventory already succeeded. This means
            // event delivery is best-effort in Phase 2 - a known gap, not hidden.
        }
    }
}
