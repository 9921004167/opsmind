package com.opsmind.core.investigation.ai;

import java.util.List;

/** The exact JSON schema Gemini is instructed to return. Every field is validated
 *  in GeminiAIInvestigator before being trusted - this record by itself does not
 *  guarantee valid content, only valid shape. */
public record GeminiRcaJson(
        String rootCause,
        String hypothesis,
        String confidenceLevel,
        Double confidenceScore,
        String affectedService,
        String impact,
        String reasoningSummary,
        List<String> recommendedActions,
        List<String> supportingEvidenceIds
) {}
