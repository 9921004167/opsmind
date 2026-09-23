package com.opsmind.core.investigation.ai;

import java.util.List;
import java.util.UUID;

/**
 * The AI's output, already validated against a strict schema by the time this
 * object exists (see GeminiAIInvestigator.parseAndValidate). rootCause/hypothesis/
 * reasoningSummary/recommendedActions are HYPOTHESIS AND RECOMMENDATION, never
 * treated as fact - RcaFinding (the persisted form) makes the same distinction
 * clear to API consumers via field naming, not just this comment.
 */
public record RcaResult(
        String rootCause,
        String hypothesis,
        String confidenceLevel,      // "LOW" | "MEDIUM" | "HIGH"
        Double confidenceScore,      // optional 0.0-1.0, nullable
        String affectedService,
        String impact,
        String reasoningSummary,
        List<String> recommendedActions,
        List<UUID> supportingEvidenceIds
) {}
