package com.opsmind.core.remediation;

/**
 * Lifecycle of a RemediationRecommendation (Section 16 of the product spec).
 *
 * PROPOSED   -> APPROVED   -> EXECUTING -> EXECUTED -> VERIFYING -> VERIFIED_RECOVERED
 * PROPOSED   -> REJECTED
 * APPROVED   -> EXECUTING  -> EXECUTION_FAILED
 * EXECUTED   -> VERIFYING  -> VERIFICATION_FAILED
 *
 * All transitions are guarded by RemediationStateMachine and audited.
 */
public enum RecommendationStatus {
    PROPOSED,
    APPROVED,
    REJECTED,
    EXECUTING,
    EXECUTED,
    EXECUTION_FAILED,
    VERIFYING,
    VERIFIED_RECOVERED,
    VERIFICATION_FAILED
}
