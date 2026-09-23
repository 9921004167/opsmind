package com.opsmind.core.investigation.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.investigation.Evidence;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class GeminiAIInvestigator implements AIInvestigator {

    private final RestClient geminiRestClient;
    private final ObjectMapper objectMapper;

    @Value("${opsmind.ai.gemini.api-key}")
    private String apiKey;

    @Value("${opsmind.ai.gemini.model}")
    private String model;

    private static final Set<String> VALID_CONFIDENCE_LEVELS = Set.of("LOW", "MEDIUM", "HIGH");

    /**
     * Transient-failure retry policy for the Gemini call. 429 (rate limited) and
     * 5xx (server-side, e.g. 503 "high demand") are worth retrying - they are
     * about Gemini's current load, not about this request being wrong. Any other
     * status (4xx like a deprecated/unknown model name) is a permanent failure:
     * retrying it would just waste attempts on something that can never succeed.
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
    public RcaResult investigate(IncidentSummary incidentSummary, List<Evidence> evidence) throws AIInvestigationException {
        if (apiKey == null || apiKey.isBlank()) {
            // Spec requirement: "If Gemini is unavailable: OpsMind must NOT crash.
            // The incident should remain active and clearly indicate: AI
            // investigation unavailable." This is the exact failure path that
            // produces that outcome (see InvestigationService).
            throw new AIInvestigationException("gemini_not_configured: GEMINI_API_KEY is not set");
        }
        if (evidence.isEmpty()) {
            throw new AIInvestigationException("no_evidence_available: cannot investigate without any collected evidence");
        }

        Map<String, UUID> evidenceIndex = new LinkedHashMap<>();
        String prompt = buildPrompt(incidentSummary, evidence, evidenceIndex);

        String rawResponseText;
        try {
            rawResponseText = callGeminiWithRetry(prompt);
        } catch (Exception e) {
            throw new AIInvestigationException("gemini_call_failed: " + e.getMessage(), e);
        }

        GeminiRcaJson parsed;
        try {
            parsed = objectMapper.readValue(rawResponseText, GeminiRcaJson.class);
        } catch (Exception e) {
            throw new AIInvestigationException("ai_response_invalid: response was not valid JSON matching the expected schema: " + e.getMessage());
        }

        return validateAndConvert(parsed, evidenceIndex);
    }

    private String buildPrompt(IncidentSummary summary, List<Evidence> evidence, Map<String, UUID> evidenceIndex) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are an SRE incident investigator. Analyze ONLY the evidence listed below. ")
          .append("Do not use any information beyond what is given here. ")
          .append("Distinguish clearly between OBSERVED evidence (what is listed below) and your own HYPOTHESIS (your interpretation). ")
          .append("Every claim you make as fact must be traceable to one of the evidence ids listed. ")
          .append("Do not include any shell command, script, API call, or infrastructure-change instruction in your response - ")
          .append("recommendedActions must be short human-readable suggestions only (e.g. \"Investigate payment-service database connection pool\"), ")
          .append("never something intended to be executed automatically.\n\n");

        sb.append("INCIDENT:\n")
          .append("number: ").append(summary.incidentNumber()).append("\n")
          .append("title: ").append(summary.title()).append("\n")
          .append("severity: ").append(summary.severity()).append("\n")
          .append("affectedService: ").append(summary.affectedServiceSlug()).append("\n\n");

        sb.append("EVIDENCE (each with a stable id you must reference by exact string if you cite it):\n");
        int i = 1;
        for (Evidence e : evidence) {
            String refId = "ev-" + i;
            evidenceIndex.put(refId, e.getId());
            sb.append(String.format("- id: %s | type: %s | source: %s | observedAt: %s | title: %s | value: %s | description: %s%n",
                    refId, e.getType(), e.getSource(), e.getObservedAt(), e.getTitle(), e.getObservedValue(), e.getDescription()));
            i++;
        }

        sb.append("\nRespond with ONLY a JSON object matching exactly this shape (no markdown, no prose outside the JSON):\n")
          .append("""
              {
                "rootCause": "string - concise identified cause",
                "hypothesis": "string - your interpretation of why this happened",
                "confidenceLevel": "LOW|MEDIUM|HIGH",
                "confidenceScore": 0.0,
                "affectedService": "string",
                "impact": "string",
                "reasoningSummary": "string - how the evidence supports the hypothesis",
                "recommendedActions": ["string", "..."],
                "supportingEvidenceIds": ["ev-1", "ev-3"]
              }
              """);
        return sb.toString();
    }

    /**
     * Retries callGemini on transient failures only (429 / 5xx). A non-retryable
     * failure (any other status, or a non-HTTP exception like a timeout the
     * client itself gives up on) is thrown immediately on the first attempt.
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
                    // e.g. 404 deprecated/unknown model, 400 bad request - will
                    // never succeed on retry, so fail fast instead of wasting
                    // attempts and time.
                    log.error("Gemini call failed with non-retryable status {}: {}", status, e.getMessage());
                    throw e;
                }

                lastFailure = e;
                if (attempt < MAX_ATTEMPTS) {
                    long backoffMillis = BASE_BACKOFF_MILLIS * (1L << (attempt - 1)); // 1s, 2s, 4s
                    log.warn("Gemini call failed with retryable status {} (attempt {}/{}); retrying in {}ms: {}",
                            status, attempt, MAX_ATTEMPTS, backoffMillis, e.getMessage());
                    sleep(backoffMillis);
                } else {
                    log.error("Gemini call failed with retryable status {} on final attempt {}/{}: {}",
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
            throw new AIInvestigationException("interrupted while waiting to retry Gemini call", ie);
        }
    }

    private String callGemini(String prompt) {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("responseMimeType", "application/json")
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
                throw new AIInvestigationException("ai_response_invalid: Gemini response had no content text. Raw: " + response);
            }
            return textNode.asText();
        } catch (AIInvestigationException e) {
            throw e;
        } catch (Exception e) {
            throw new AIInvestigationException("ai_response_invalid: could not parse Gemini's outer response envelope: " + e.getMessage());
        }
    }

    private RcaResult validateAndConvert(GeminiRcaJson parsed, Map<String, UUID> evidenceIndex) {
        List<String> errors = new ArrayList<>();
        if (isBlank(parsed.rootCause())) errors.add("rootCause is required");
        if (isBlank(parsed.hypothesis())) errors.add("hypothesis is required");
        if (isBlank(parsed.reasoningSummary())) errors.add("reasoningSummary is required");
        if (parsed.recommendedActions() == null || parsed.recommendedActions().isEmpty()) errors.add("recommendedActions must be non-empty");
        String confidenceLevel = parsed.confidenceLevel() == null ? null : parsed.confidenceLevel().trim().toUpperCase();
        if (confidenceLevel == null || !VALID_CONFIDENCE_LEVELS.contains(confidenceLevel)) {
            errors.add("confidenceLevel must be one of LOW, MEDIUM, HIGH");
        }
        if (parsed.confidenceScore() != null && (parsed.confidenceScore() < 0.0 || parsed.confidenceScore() > 1.0)) {
            errors.add("confidenceScore must be between 0.0 and 1.0");
        }
        if (!errors.isEmpty()) {
            throw new AIInvestigationException("ai_response_invalid: " + String.join("; ", errors));
        }

        // Drop any cited evidence id the model hallucinated that doesn't correspond
        // to something actually passed in - never trust the model's own bookkeeping.
        List<UUID> supportingIds = parsed.supportingEvidenceIds() == null ? List.of() :
                parsed.supportingEvidenceIds().stream()
                        .map(evidenceIndex::get)
                        .filter(java.util.Objects::nonNull)
                        .collect(Collectors.toList());
        if (supportingIds.isEmpty()) {
            log.warn("Gemini response cited no valid supporting evidence ids - keeping the finding but flagging this in logs");
        }

        return new RcaResult(parsed.rootCause(), parsed.hypothesis(), confidenceLevel, parsed.confidenceScore(),
                parsed.affectedService(), parsed.impact(), parsed.reasoningSummary(), parsed.recommendedActions(), supportingIds);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}