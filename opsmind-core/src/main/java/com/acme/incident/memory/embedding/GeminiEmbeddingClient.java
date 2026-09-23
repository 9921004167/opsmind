
package com.acme.incident.memory.embedding;

import com.acme.incident.memory.config.IncidentMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Thin client over the gemini-embedding-2 embedContent endpoint.
 *
 * Task instructions are included in the text sent to Gemini so that
 * DOCUMENT and QUERY embeddings are shaped for their intended use.
 */
@Component
public class GeminiEmbeddingClient {

    private static final Logger log =
            LoggerFactory.getLogger(GeminiEmbeddingClient.class);

    private final RestClient client;
    private final IncidentMemoryProperties.Embedding cfg;

    public GeminiEmbeddingClient(
            RestClient.Builder builder,
            IncidentMemoryProperties props) {

        this.cfg = props.embedding();

        this.client = builder
                .baseUrl(cfg.baseUrl())
                .defaultHeader("x-goog-api-key", cfg.apiKey())
                .defaultHeader(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    public String model() {
        return cfg.model();
    }

    public int dimensions() {
        return cfg.dimensions();
    }

    public float[] embed(String text, EmbeddingTask task) {

        if (text == null || text.isBlank()) {
            throw new EmbeddingException(
                    "refusing to embed blank text",
                    false,
                    null
            );
        }

        /*
         * Gemini Embedding 2 uses the text itself to carry the
         * task instruction.
         */
        String instructedText =
                task.instruction() + " | content: " + text;

        Map<String, Object> body = Map.of(
                "model", "models/" + cfg.model(),

                "content", Map.of(
                        "parts", List.of(
                                Map.of("text", instructedText)
                        )
                ),

                "output_dimensionality", cfg.dimensions()
        );

        EmbedResponse res;

        try {

            res = client.post()
                    .uri(
                            "/v1beta/models/{model}:embedContent",
                            cfg.model()
                    )
                    .body(body)
                    .exchange((req, resp) -> {

                        HttpStatusCode status =
                                resp.getStatusCode();

                        if (status.is2xxSuccessful()) {
                            return resp.bodyTo(
                                    EmbedResponse.class
                            );
                        }

                        boolean retryable =
                                status.value() == 429
                                        || status.is5xxServerError();

                        throw new EmbeddingException(
                                "gemini embedding failed: HTTP "
                                        + status.value(),
                                retryable,
                                null
                        );
                    });

        } catch (EmbeddingException e) {

            throw e;

        } catch (Exception e) {

            // Timeouts and connection resets are worth another attempt.
            throw new EmbeddingException(
                    "gemini embedding call failed",
                    true,
                    e
            );
        }

        if (res == null
                || res.embedding() == null
                || res.embedding().values() == null) {

            throw new EmbeddingException(
                    "gemini returned no embedding",
                    true,
                    null
            );
        }

        float[] v = res.embedding().values();

        if (v.length != cfg.dimensions()) {

            log.warn(
                    "expected {} dims from {}, got {}",
                    cfg.dimensions(),
                    cfg.model(),
                    v.length
            );

            throw new EmbeddingException(
                    "unexpected embedding dimensionality",
                    false,
                    null
            );
        }

        return normalize(v);
    }

    /**
     * L2-normalize.
     * Required after MRL truncation; harmless otherwise.
     */
    static float[] normalize(float[] v) {

        double sumSq = 0;

        for (float x : v) {
            sumSq += (double) x * x;
        }

        if (sumSq == 0.0) {
            throw new EmbeddingException(
                    "degenerate embedding vector",
                    false,
                    null
            );
        }

        float norm = (float) Math.sqrt(sumSq);

        float[] normalized = new float[v.length];

        for (int i = 0; i < v.length; i++) {
            normalized[i] = v[i] / norm;
        }

        return normalized;
    }

    record EmbedResponse(Values embedding) {

    }

    record Values(float[] values) {

    }
}

