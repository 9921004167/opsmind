package com.acme.incident.memory.port;

import java.util.Optional;
import java.util.UUID;

/**
 * Implement this in your existing incident module (phases 1-6). It is the
 * only seam between Phase 7 and the rest of the system.
 *
 * Both methods MUST scope by organization_id; Phase 7 passes it through to
 * every read and write, but the source of truth for tenancy is your side.
 */
public interface IncidentMemorySourcePort {

    /** Full post-mortem for an incident that has just been resolved. */
    Optional<ResolvedIncidentView> loadResolved(UUID organizationId, UUID incidentId);

    /** Current state of an incident under investigation. */
    Optional<ActiveIncidentView> loadActive(UUID organizationId, UUID incidentId);
}
