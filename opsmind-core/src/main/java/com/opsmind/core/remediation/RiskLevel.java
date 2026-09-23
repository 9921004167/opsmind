package com.opsmind.core.remediation;

/**
 * Fixed risk classification. Never derived from Gemini's opinion - always from
 * the matched RunbookDefinition's own riskLevel column (see Section 10 of the
 * product spec). An unmatched/hallucinated runbook key is always treated as HIGH.
 */
public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH
}
