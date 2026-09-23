package com.acme.incident.memory.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All Phase 7 tuning lives here. Defaults target a corpus in the low
 * thousands of resolved incidents per organization.
 */
@ConfigurationProperties(prefix = "incident-memory")
public record IncidentMemoryProperties(
        Embedding embedding,
        Worker worker,
        Retrieval retrieval) {

    public record Embedding(
            String model,
            int dimensions,
            String baseUrl,
            String apiKey,
            Duration timeout,
            int maxDocumentChars) {

        public Embedding {
            if (model == null) model = "gemini-embedding-2";
            if (dimensions == 0) dimensions = 1536;
            if (baseUrl == null) baseUrl = "https://generativelanguage.googleapis.com";
            if (timeout == null) timeout = Duration.ofSeconds(20);
            // gemini-embedding-2 accepts 8192 input tokens; ~3 chars/token, with margin.
            if (maxDocumentChars == 0) maxDocumentChars = 22_000;
        }
    }

    public record Worker(int batchSize, int maxAttempts, Duration baseBackoff) {
        public Worker {
            if (batchSize == 0) batchSize = 20;
            if (maxAttempts == 0) maxAttempts = 5;
            if (baseBackoff == null) baseBackoff = Duration.ofSeconds(30);
        }
    }

    public record Retrieval(
            int topK,
            int candidateMultiplier,
            double minSimilarity,
            int efSearch,
            Duration maxAge,
            double weightSimilarity,
            double weightServiceOverlap,
            double weightRecency) {

        public Retrieval {
            if (topK == 0) topK = 5;
            // Over-fetch, then rerank and threshold in the application.
            if (candidateMultiplier == 0) candidateMultiplier = 4;
            // Below this a "similar incident" is noise, and noise invites the
            // LLM to invent a connection. Tune against your own corpus.
            if (minSimilarity == 0) minSimilarity = 0.62;
            if (efSearch == 0) efSearch = 100;
            if (maxAge == null) maxAge = Duration.ofDays(540);
            if (weightSimilarity == 0) weightSimilarity = 0.75;
            if (weightServiceOverlap == 0) weightServiceOverlap = 0.15;
            if (weightRecency == 0) weightRecency = 0.10;
        }
    }
}
