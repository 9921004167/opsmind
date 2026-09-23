package com.opsmind.core.incident;

import com.opsmind.core.common.DomainException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for which incident status transitions are legal.
 * Every phase of OpsMind (correlation, AI investigation, remediation, verification)
 * moves incidents through this machine rather than setting status directly - so
 * this class, not scattered service code, is what "the incident lifecycle" means.
 */
public final class IncidentStateMachine {

    private static final Map<IncidentStatus, Set<IncidentStatus>> TRANSITIONS = new EnumMap<>(IncidentStatus.class);

    static {
        TRANSITIONS.put(IncidentStatus.DETECTED, EnumSet.of(IncidentStatus.NEW));
        TRANSITIONS.put(IncidentStatus.NEW, EnumSet.of(IncidentStatus.INVESTIGATING, IncidentStatus.ESCALATED));
        TRANSITIONS.put(IncidentStatus.INVESTIGATING, EnumSet.of(
                IncidentStatus.AWAITING_APPROVAL, IncidentStatus.MITIGATING, IncidentStatus.ESCALATED));
        TRANSITIONS.put(IncidentStatus.AWAITING_APPROVAL, EnumSet.of(IncidentStatus.REMEDIATING, IncidentStatus.ESCALATED));
        TRANSITIONS.put(IncidentStatus.MITIGATING, EnumSet.of(IncidentStatus.VERIFYING, IncidentStatus.ESCALATED));
        TRANSITIONS.put(IncidentStatus.REMEDIATING, EnumSet.of(IncidentStatus.VERIFYING, IncidentStatus.ESCALATED));
        TRANSITIONS.put(IncidentStatus.VERIFYING, EnumSet.of(
                IncidentStatus.RESOLVED, IncidentStatus.INVESTIGATING, IncidentStatus.ESCALATED));
        TRANSITIONS.put(IncidentStatus.RESOLVED, EnumSet.of(IncidentStatus.CLOSED));
        TRANSITIONS.put(IncidentStatus.ESCALATED, EnumSet.of(IncidentStatus.INVESTIGATING, IncidentStatus.CLOSED));
        TRANSITIONS.put(IncidentStatus.CLOSED, EnumSet.noneOf(IncidentStatus.class));
    }

    private IncidentStateMachine() {}

    public static boolean canTransition(IncidentStatus from, IncidentStatus to) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(IncidentStatus.class)).contains(to);
    }

    /** @throws DomainException if the transition is not legal. */
    public static void validateTransition(IncidentStatus from, IncidentStatus to) {
        if (!canTransition(from, to)) {
            throw new DomainException(
                    "Illegal incident state transition: " + from + " -> " + to +
                    ". Allowed from " + from + ": " + TRANSITIONS.getOrDefault(from, Set.of()));
        }
    }

    public static Set<IncidentStatus> allowedNext(IncidentStatus from) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(IncidentStatus.class));
    }
}
