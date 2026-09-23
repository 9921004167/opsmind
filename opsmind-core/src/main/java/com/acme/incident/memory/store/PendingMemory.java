package com.acme.incident.memory.store;

import java.util.UUID;

/** A row claimed by the embedding worker. */
public record PendingMemory(UUID id, UUID organizationId, UUID incidentId, String content, int attempts) { }
