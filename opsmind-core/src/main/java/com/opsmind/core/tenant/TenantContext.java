package com.opsmind.core.tenant;

import java.util.UUID;

/**
 * The authenticated caller's tenant identity, resolved from the JWT claims by
 * JwtAuthFilter and made available to controllers/services via Spring Security's
 * Authentication#getPrincipal(). Every tenant-scoped query must go through this,
 * never a client-supplied organizationId, to keep tenant isolation real.
 */
public record TenantContext(UUID userId, UUID organizationId, Role role) {
}
