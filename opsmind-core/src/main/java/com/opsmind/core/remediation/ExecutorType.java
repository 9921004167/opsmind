package com.opsmind.core.remediation;

/**
 * Which real executor a RunbookDefinition maps to.
 * FAULT_DISABLE - the only real automation that exists (Phase 4 /faults/*\/disable).
 * MANUAL        - no automated executor exists; a human must perform the action.
 *                 Never becomes executable, regardless of matched risk level.
 */
public enum ExecutorType {
    FAULT_DISABLE,
    MANUAL
}
