package com.opsmind.core.remediation.event;

/**
 * Kept separate from KafkaTopics, same reasoning as InvestigationTopics: this
 * package doesn't assume the exact current state of that file. Note the values
 * here use dot-separated names distinct from KafkaTopics' placeholder
 * REMEDIATION_* constants (which are reserved names Phase 1 never produced to) -
 * safe to consolidate later if you'd rather have one topic registry.
 */
public final class RemediationTopics {
    public static final String REMEDIATION_RECOMMENDED = "remediation.recommended";
    public static final String REMEDIATION_APPROVED = "remediation.approved";
    public static final String REMEDIATION_REJECTED = "remediation.rejected";
    public static final String REMEDIATION_EXECUTED = "remediation.executed";
    public static final String REMEDIATION_EXECUTION_FAILED = "remediation.execution.failed";
    public static final String REMEDIATION_VERIFIED = "remediation.verified";
    private RemediationTopics() {}
}
