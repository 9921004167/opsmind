package com.opsmind.core.remediation;

import com.opsmind.core.common.DomainException;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Single source of truth for legal RemediationRecommendation transitions
 * (Section 16). Mirrors the pattern of com.opsmind.core.incident.IncidentStateMachine.
 */
public final class RemediationStateMachine {

    private static final Map<RecommendationStatus, Set<RecommendationStatus>> TRANSITIONS = new EnumMap<>(RecommendationStatus.class);

    static {
        TRANSITIONS.put(RecommendationStatus.PROPOSED, EnumSet.of(
                RecommendationStatus.APPROVED, RecommendationStatus.REJECTED));
        TRANSITIONS.put(RecommendationStatus.APPROVED, EnumSet.of(
                RecommendationStatus.EXECUTING));
        TRANSITIONS.put(RecommendationStatus.EXECUTING, EnumSet.of(
                RecommendationStatus.EXECUTED, RecommendationStatus.EXECUTION_FAILED));
        TRANSITIONS.put(RecommendationStatus.EXECUTED, EnumSet.of(
                RecommendationStatus.VERIFYING));
        TRANSITIONS.put(RecommendationStatus.VERIFYING, EnumSet.of(
                RecommendationStatus.VERIFIED_RECOVERED, RecommendationStatus.VERIFICATION_FAILED));
        TRANSITIONS.put(RecommendationStatus.REJECTED, EnumSet.noneOf(RecommendationStatus.class));
        TRANSITIONS.put(RecommendationStatus.EXECUTION_FAILED, EnumSet.noneOf(RecommendationStatus.class));
        TRANSITIONS.put(RecommendationStatus.VERIFIED_RECOVERED, EnumSet.noneOf(RecommendationStatus.class));
        TRANSITIONS.put(RecommendationStatus.VERIFICATION_FAILED, EnumSet.noneOf(RecommendationStatus.class));
    }

    private RemediationStateMachine() {}

    public static boolean canTransition(RecommendationStatus from, RecommendationStatus to) {
        return TRANSITIONS.getOrDefault(from, EnumSet.noneOf(RecommendationStatus.class)).contains(to);
    }

    /** @throws DomainException if the transition is not legal. */
    public static void validateTransition(RecommendationStatus from, RecommendationStatus to) {
        if (!canTransition(from, to)) {
            throw new DomainException(
                    "Illegal remediation state transition: " + from + " -> " + to +
                    ". Allowed from " + from + ": " + TRANSITIONS.getOrDefault(from, Set.of()));
        }
    }
}
