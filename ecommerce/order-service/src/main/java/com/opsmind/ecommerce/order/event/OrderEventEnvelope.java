package com.opsmind.ecommerce.order.event;

import java.time.Instant;
import java.util.UUID;

/** Lightweight version of opsmind-core's EventEnvelope - order-service is a
 *  separate deployable and does not share code with opsmind-core, only the Kafka
 *  cluster and a consistent envelope shape. */
public record OrderEventEnvelope<T>(UUID eventId, String eventType, Instant occurredAt, T payload) {
    public static <T> OrderEventEnvelope<T> of(String eventType, T payload) {
        return new OrderEventEnvelope<>(UUID.randomUUID(), eventType, Instant.now(), payload);
    }
}
