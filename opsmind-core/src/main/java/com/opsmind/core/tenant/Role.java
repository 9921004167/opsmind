package com.opsmind.core.tenant;

/**
 * Roles as defined in the product spec (Section 19):
 * ADMIN    - full control over the organization, including user management
 * SRE      - can investigate and approve/execute remediation (future phases)
 * ENGINEER - can investigate incidents and update status, cannot manage org/users
 * VIEWER   - read-only
 */
public enum Role {
    ADMIN,
    SRE,
    ENGINEER,
    VIEWER
}
