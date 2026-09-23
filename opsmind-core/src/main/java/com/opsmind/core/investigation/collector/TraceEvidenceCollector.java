package com.opsmind.core.investigation.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.investigation.Evidence;
import com.opsmind.core.investigation.EvidenceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * FLAGGED LIMITATION: Tempo's query API/schema differs across versions (TraceQL
 * search endpoint vs the older tag-based /api/search). This implementation targets
 * the TraceQL search endpoint (`/api/search?q=...`), which is what current Tempo
 * (2.x+) exposes. If your Tempo setup is older or configured differently, this
 * collector will log a warning and return no trace evidence rather than fabricate
 * results - adjust buildQuery()/parseResponse() to match your actual Tempo version.
 *
 * Two searches are run: one for ALL traces touching the service (to show volume/
 * baseline), and one specifically for error traces (status=error), since an error
 * trace set is much more useful evidence than an unfiltered trace count.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TraceEvidenceCollector implements EvidenceCollector {

    private static final int MAX_TRACES_TO_LIST = 5;

    private final RestClient tempoRestClient;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "tempo";
    }

    @Override
    public List<Evidence> collect(EvidenceCollectionContext context) {
        List<Evidence> evidence = new ArrayList<>();
        if (context.serviceSlug() == null) {
            log.info("Skipping trace evidence collection - incident has no primary service");
            return evidence;
        }

        long startSeconds = context.windowStart().getEpochSecond();
        long endSeconds = context.windowEnd().getEpochSecond();

        collectErrorTraces(context, startSeconds, endSeconds, evidence);
        return evidence;
    }

    private void collectErrorTraces(EvidenceCollectionContext context, long start, long end, List<Evidence> evidence) {
        String traceQl = String.format("{ resource.service.name=\"%s\" && status=error }", context.serviceSlug());
        try {
            String response = tempoRestClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/api/search")
                            .queryParam("q", traceQl)
                            .queryParam("start", start)
                            .queryParam("end", end)
                            .queryParam("limit", MAX_TRACES_TO_LIST)
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = objectMapper.readTree(response);
            JsonNode traces = root.path("traces");
            if (!traces.isArray()) {
                log.warn("Unexpected Tempo response shape (no 'traces' array) - skipping trace evidence. Raw: {}", response);
                return;
            }
            if (traces.isEmpty()) {
                evidence.add(Evidence.builder()
                        .investigationId(context.investigationId())
                        .type(EvidenceType.TRACE)
                        .source("tempo")
                        .observedAt(context.windowEnd())
                        .title("No error traces found for " + context.serviceSlug())
                        .description("TraceQL search for error-status traces returned zero results in the incident window")
                        .observedValue("0")
                        .build());
                return;
            }

            evidence.add(Evidence.builder()
                    .investigationId(context.investigationId())
                    .type(EvidenceType.TRACE)
                    .source("tempo")
                    .observedAt(context.windowEnd())
                    .title("Error traces found for " + context.serviceSlug())
                    .description(traces.size() + " trace(s) with error status touching " + context.serviceSlug() + " during the incident window")
                    .observedValue(String.valueOf(traces.size()))
                    .metadata(objectMapper.writeValueAsString(java.util.Map.of("traceQl", traceQl)))
                    .build());

            for (JsonNode trace : traces) {
                String traceId = trace.path("traceID").asText(null);
                String rootService = trace.path("rootServiceName").asText(null);
                String rootName = trace.path("rootTraceName").asText(null);
                long durationMs = trace.path("durationMs").asLong(-1);
                if (traceId == null) continue;

                evidence.add(Evidence.builder()
                        .investigationId(context.investigationId())
                        .type(EvidenceType.TRACE)
                        .source("tempo")
                        .observedAt(context.windowEnd())
                        .title("Trace " + traceId)
                        .description(String.format("Root service: %s, root span: %s, duration: %sms",
                                rootService, rootName, durationMs >= 0 ? durationMs : "unknown"))
                        .observedValue(traceId)
                        .build());
            }
        } catch (Exception e) {
            log.warn("Tempo trace query failed for {} (this is non-fatal - investigation continues without trace evidence): {}",
                    context.serviceSlug(), e.getMessage());
        }
    }
}
