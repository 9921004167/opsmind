package com.opsmind.core.remediation.ai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiRemediationJson(
        String runbookKey,
        String rationale,
        String freeTextRecommendation) {}
