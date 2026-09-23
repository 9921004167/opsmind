package com.opsmind.core.remediation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.investigation.RcaFinding;
import com.opsmind.core.remediation.RunbookDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

/**
 * Gemini call #2 (Section 18). Receives ONLY the incident summary, the RcaFinding,
 * and the closed runbook catalog (key/title/description/riskLevel - never the
 * execution endpoint, which the model has no reason to see). Mirrors
 * GeminiAIInvestigator's structure/config so the two AI integrations stay
 * consistent, including retry-on-transient-failure. The returned runbookKey is
 * treated as completely untrusted - validation against the real DB catalog
 * happens in RemediationRecommendationService, not here.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiRemediationAdvisor implements RemediationAdvisor {

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;

    @Value("${opsmind.ai.gemini.api-key}")
    private String apiKey;

    @Value("${opsmind.ai.gemini.model}")
    private String model;

    /**
     * Same transient-failure retry policy as GeminiAIInvestigator: 429 (rate
     * limited) and 5xx (server-side, e.g. 503 "high demand") are worth retrying;
     * any other status is a permanent failure and fails immediately.
     */
    private static final int MAX_ATTEMPTS = 3;
    private static final long BASE_BACKOFF_MILLIS = 1000L;

    @Override
    public String providerName() {
        return "gemini";
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public RemediationAdvice recommend(IncidentSummary incidentSummary, RcaFinding rcaFinding, List<RunbookDefinition> catalog)
            throws RemediationAdvisorException {
        if (apiKey == null || apiKey.isBlank()) {
            throw new RemediationAdvisorException("gemini_not_configured: GEMINI_API_KEY is not set");
        }
        if (rcaFinding == null) {
            throw new RemediationAdvisorException("no_rca_finding_available: cannot recommend remediation without a completed investigation");
        }

        String prompt = buildPrompt(incidentSummary, rcaFinding, catalog);

        String rawResponseText;
        try {
            rawResponseText = callGeminiWithRetry(prompt);
        } catch (Exception e) {
            throw new RemediationAdvisorException("gemini_call_failed: " + e.getMessage(), e);
        }

        GeminiRemediationJson parsed;
        try {
            parsed = objectMapper.readValue(rawResponseText, GeminiRemediationJson.class);
        } catch (Exception e) {
            throw new RemediationAdvisorException("ai_response_invalid: response was not valid JSON matching the expected schema: " + e.getMessage());
        }

        if (isBlank(parsed.freeTextRecommendation())) {
            throw new RemediationAdvisorException("ai_response_invalid: freeTextRecommendation is required");
        }

        String suggestedKey = isBlank(parsed.runbookKey()) || "NONE".equalsIgnoreCase(parsed.runbookKey().trim())
                ? null : parsed.runbookKey().trim();

        return new RemediationAdvice(suggestedKey, parsed.rationale(), parsed.freeTextRecommendation(), rawResponseText);
    }

    private String buildPrompt(IncidentSummary summary, RcaFinding rcaFinding, List<RunbookDefinition> catalog) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an SRE remediation advisor. You may ONLY select a runbook by its exact 'key' from the ")
          .append("CLOSED CATALOG below - you must never invent a key, and you must never describe a shell command, ")
          .append("script, API call, or infrastructure-change instruction anywhere in your response. ")
          .append("If none of the catalog runbooks are appropriate for this incident, set runbookKey to \"NONE\". ")
          .append("freeTextRecommendation must always be a short human-readable suggestion for a person to act on manually - ")
          .append("it is never executed automatically, regardless of what you write there.\n\n");

        sb.append("INCIDENT:\n")
          .append("number: ").append(summary.incidentNumber()).append("\n")
          .append("title: ").append(summary.title()).append("\n")
          .append("severity: ").append(summary.severity()).append("\n")
          .append("affectedService: ").append(summary.affectedServiceSlug()).append("\n\n");

        sb.append("RCA FINDING:\n")
          .append("rootCause: ").append(rcaFinding.getRootCause()).append("\n")
          .append("hypothesis: ").append(rcaFinding.getHypothesis()).append("\n")
          .append("confidenceLevel: ").append(rcaFinding.getConfidenceLevel()).append("\n")
          .append("impact: ").append(rcaFinding.getImpact()).append("\n\n");

        sb.append("CLOSED RUNBOOK CATALOG (you may ONLY choose a key from this list, or \"NONE\"):\n");
        for (RunbookDefinition r : catalog) {
            sb.append(String.format("- key: %s | title: %s | riskLevel: %s | description: %s%n",
                    r.getKey(), r.getTitle(), r.getRiskLevel(), r.getDescription()));
        }

        sb.append("\nRespond with ONLY a JSON object matching exactly this shape (no markdown, no prose outside the JSON):\n")
          .append("""
              {
                "runbookKey": "string - an exact key from the catalog above, or \\"NONE\\"",
                "rationale": "string - why this runbook fits, or why none do",
                "freeTextRecommendation": "string - short human-readable suggestion, never a command"
              }
              """);
        return sb.toString();
    }

    /**
     * Retries callGemini on transient failures only (429 / 5xx). A
     * non-retryable failure is thrown immediately on the first attempt.
     */
    private String callGeminiWithRetry(String prompt) {
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return callGemini(prompt);
            } catch (RestClientResponseException e) {
                int status = e.getStatusCode().value();
                boolean retryable = status == 429 || (status >= 500 && status <= 599);

                if (!retryable) {
                    log.error("Gemini remediation call failed with non-retryable status {}: {}", status, e.getMessage());
                    throw e;
                }

                lastFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    long backoffMillis = BASE_BACKOFF_MILLIS * (1L << (attempt - 1)); // 1s, 2s, 4s
                    log.warn("Gemini remediation call failed with retryable status {} (attempt {}/{}); retrying in {}ms: {}",
                            status, attempt, MAX_ATTEMPTS, backoffMillis, e.getMessage());
                    sleep(backoffMillis);
                } else {
                    log.error("Gemini remediation call failed with retryable status {} on final attempt {}/{}: {}",
                            status, attempt, MAX_ATTEMPTS, e.getMessage());
                }
            }
        }

        throw lastFailure;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RemediationAdvisorException("interrupted while waiting to retry Gemini call", ie);
        }
    }

    private String callGemini(String prompt) {
        java.util.Map<String, Object> body = java.util.Map.of(
                "contents", List.of(java.util.Map.of("parts", List.of(java.util.Map.of("text", prompt)))),
                "generationConfig", java.util.Map.of("responseMimeType", "application/json")
        );

        String response = geminiRestClient.post()
                .uri("/v1beta/models/{model}:generateContent?key={key}", model, apiKey)
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(response);
            JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
            if (textNode.isMissingNode() || textNode.asText().isBlank()) {
                throw new RemediationAdvisorException("ai_response_invalid: Gemini response had no content text. Raw: " + response);
            }
            return textNode.asText();
        } catch (RemediationAdvisorException e) {
            throw e;
        } catch (Exception e) {
            throw new RemediationAdvisorException("ai_response_invalid: could not parse Gemini's outer response envelope: " + e.getMessage());
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}