package com.acme.incident.memory.ingest;

import java.util.UUID;

/**
 * Publish this from your existing resolution use case (phase 2/5) with
 * ApplicationEventPublisher, inside the transaction that resolves the
 * incident. The listener runs AFTER_COMMIT.
 */
public record IncidentResolvedEvent(UUID organizationId, UUID incidentId) { }
