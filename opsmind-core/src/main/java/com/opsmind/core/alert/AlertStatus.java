package com.opsmind.core.alert;

public enum AlertStatus {
    /** Received and stored, not yet linked to an incident. Reserved for Phase 6 correlation. */
    RECEIVED,
    /** Linked to an incident (Phase 1: always immediately, via naive 1:1 mapping). */
    LINKED
}
