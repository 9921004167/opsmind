package com.opsmind.core.kafka;

import java.time.Instant;
import java.util.UUID;

/**
 * Common envelope for every event OpsMind publishes to Kafka. Wrapping the payload
 * this way (rather than publishing raw domain objects) means every consumer -
 * including ones written in later phases - can rely on a stable, versioned shape
 * regardless of what changes inside individual payload types.
 */
public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        Instant occurredAt,
        UUID organizationId,
        T payload
) {
    public static <T> EventEnvelope<T> of(String eventType, UUID organizationId, T payload) {
        return new EventEnvelope<>(UUID.randomUUID(), eventType, Instant.now(), organizationId, payload);
    }
}
