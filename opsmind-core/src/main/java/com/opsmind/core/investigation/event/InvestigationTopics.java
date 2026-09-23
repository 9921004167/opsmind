package com.opsmind.core.investigation.event;

/**
 * Kept separate from the existing KafkaTopics class (rather than editing it
 * directly) since this package doesn't know the exact current state of that file
 * in your repo after Phase 2-3 changes. Safe to consolidate into KafkaTopics later
 * if you'd rather have one topic registry - see PHASE6-MERGE-INSTRUCTIONS.md.
 */
public final class InvestigationTopics {
    public static final String INVESTIGATION_STARTED = "investigation.started";
    public static final String INVESTIGATION_COMPLETED = "investigation.completed";
    public static final String INVESTIGATION_FAILED = "investigation.failed";
    private InvestigationTopics() {}
}
