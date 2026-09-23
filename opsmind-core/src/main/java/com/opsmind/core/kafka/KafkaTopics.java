package com.opsmind.core.kafka;

/**
 * The full event vocabulary from the product spec (Section 22). Declaring every
 * topic name here now - even ones no producer emits to yet - keeps later phases
 * consistent instead of each phase inventing its own naming.
 *
 * Topics marked "Phase 1: PRODUCED" are actually published to by this codebase.
 * All others are reserved names for future phases and are NOT produced yet -
 * treat any reference to them elsewhere as a contract, not a working feature.
 */
public final class KafkaTopics {

    // Phase 1: PRODUCED
    public static final String ALERT_RECEIVED = "alert.received";
    public static final String INCIDENT_CREATED = "incident.created";
    public static final String INCIDENT_STATUS_CHANGED = "incident.status.changed";

    // Reserved for later phases - not produced by Phase 1 code
    public static final String INCIDENT_INVESTIGATION_STARTED = "incident.investigation.started";
    public static final String INCIDENT_INVESTIGATION_COMPLETED = "incident.investigation.completed";
    public static final String REMEDIATION_REQUESTED = "remediation.requested";
    public static final String REMEDIATION_APPROVED = "remediation.approved";
    public static final String REMEDIATION_EXECUTED = "remediation.executed";
    public static final String REMEDIATION_FAILED = "remediation.failed";
    public static final String INCIDENT_VERIFICATION_STARTED = "incident.verification.started";
    public static final String INCIDENT_RESOLVED = "incident.resolved";

    private KafkaTopics() {}
}
