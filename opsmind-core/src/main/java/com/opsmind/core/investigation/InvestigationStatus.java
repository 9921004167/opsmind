package com.opsmind.core.investigation;

/**
 * PENDING: created, evidence collection not yet started.
 * RUNNING: evidence collection and/or AI reasoning in progress.
 * COMPLETED: RcaFinding persisted successfully.
 * FAILED: evidence collection may have partially succeeded (those Evidence rows
 *         are kept - they are real facts, not invalidated by a later AI failure),
 *         but no RcaFinding was produced. failureReason explains why (e.g.
 *         "gemini_not_configured", "ai_response_invalid: <detail>").
 */
public enum InvestigationStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}
