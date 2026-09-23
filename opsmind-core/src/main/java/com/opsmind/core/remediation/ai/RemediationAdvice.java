package com.opsmind.core.remediation.ai;

/**
 * Raw advisor output, BEFORE catalog validation. suggestedRunbookKey is
 * untrusted input - RemediationRecommendationService is the only place that
 * decides whether it becomes a real, executable recommendation.
 */
public record RemediationAdvice(
        String suggestedRunbookKey,
        String rationale,
        String freeTextRecommendation,
        String rawJson) {}
