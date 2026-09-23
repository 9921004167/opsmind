package com.opsmind.core.incident;

/**
 * Full lifecycle per Section 16 of the spec. DETECTED is reserved for a future
 * pre-incident "signal observed, correlation in progress" state (Phase 6) - Phase 1
 * incidents are created directly at NEW, since naive 1:1 alert mapping has nothing
 * to correlate.
 */
public enum IncidentStatus {
    DETECTED,
    NEW,
    INVESTIGATING,
    MITIGATING,
    AWAITING_APPROVAL,
    REMEDIATING,
    VERIFYING,
    RESOLVED,
    ESCALATED,
    CLOSED
}
