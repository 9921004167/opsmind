package com.opsmind.core.investigation.collector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.opsmind.core.investigation.Evidence;
import com.opsmind.core.investigation.EvidenceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PrometheusEvidenceCollector implements EvidenceCollector {

    private final RestClient prometheusRestClient;
    private final ObjectMapper objectMapper;

    private record NamedQuery(
            String name,
            String title,
            String promqlTemplate,
            String unit
    ) {}

    private static final List<NamedQuery> QUERIES = List.of(

            new NamedQuery(
                    "5xx_rate",
                    "HTTP 5xx error rate",
                    "max_over_time((sum(rate(http_server_requests_seconds_count{job=\"%s\",status=~\"5..\"}[1m])) / sum(rate(http_server_requests_seconds_count{job=\"%s\"}[1m])))[%ss:15s])",
                    "ratio"
            ),

            new NamedQuery(
                    "p95_latency",
                    "P95 request latency",
                    "max_over_time(histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"%s\"}[1m])) by (le))[%ss:15s])",
                    "seconds"
            ),

            new NamedQuery(
                    "avg_latency",
                    "Average request latency (fallback: histogram unavailable)",
                    "max_over_time((sum(rate(http_server_requests_seconds_sum{job=\"%s\"}[1m])) / sum(rate(http_server_requests_seconds_count{job=\"%s\"}[1m])))[%ss:15s])",
                    "seconds"
            ),

            new NamedQuery(
                    "request_rate",
                    "Request rate",
                    "max_over_time(sum(rate(http_server_requests_seconds_count{job=\"%s\"}[1m]))[%ss:15s])",
                    "req/s"
            ),

            new NamedQuery(
                    "jvm_heap_used",
                    "JVM heap memory used",
                    "max_over_time(sum(jvm_memory_used_bytes{job=\"%s\",area=\"heap\"})[%ss:15s])",
                    "bytes"
            ),

            new NamedQuery(
                    "process_cpu",
                    "Process CPU usage",
                    "max_over_time(process_cpu_usage{job=\"%s\"}[%ss:15s])",
                    "ratio"
            )
    );

    @Override
    public String name() {
        return "prometheus";
    }

    @Override
    public List<Evidence> collect(EvidenceCollectionContext context) {

        List<Evidence> evidence = new ArrayList<>();

        if (context.serviceSlug() == null) {
            log.info(
                    "Skipping Prometheus evidence collection - incident has no primary service"
            );
            return evidence;
        }

        String windowSpec = boundedWindowSpec(
                context.windowStart(),
                context.windowEnd()
        );

        /*
         * Prometheus up metric uses the scrape job label.
         */
        try {

            String upQuery =
                    "min_over_time(up{job=\"" +
                    context.serviceSlug() +
                    "\"}[" +
                    windowSpec +
                    ":15s])";

            queryScalar(upQuery).ifPresent(value ->
                    evidence.add(
                            buildEvidence(
                                    context,
                                    "Service availability (Prometheus up)",
                                    value == 1.0 ? "up" : "down (0)",
                                    "boolean",
                                    upQuery
                            )
                    )
            );

        } catch (Exception e) {

            log.warn(
                    "Prometheus 'up' query failed for {}: {}",
                    context.serviceSlug(),
                    e.getMessage()
            );
        }

        /*
         * Execute all metric queries.
         */
        for (NamedQuery q : QUERIES) {

            try {

                String promql;

                if ("5xx_rate".equals(q.name()) || "avg_latency".equals(q.name())) {

                    promql = String.format(
                            q.promqlTemplate(),
                            context.serviceSlug(),
                            context.serviceSlug(),
                            windowSpec.replace("s", "")
                    );

                } else {

                    promql = String.format(
                            q.promqlTemplate(),
                            context.serviceSlug(),
                            windowSpec.replace("s", "")
                    );
                }

                queryScalar(promql).ifPresent(value ->
                        evidence.add(
                                buildEvidence(
                                        context,
                                        q.title(),
                                        formatValue(value, q.unit()),
                                        q.unit(),
                                        promql
                                )
                        )
                );

            } catch (Exception e) {

                log.warn(
                        "Prometheus query '{}' failed for {}: {}",
                        q.name(),
                        context.serviceSlug(),
                        e.getMessage()
                );
            }
        }

        return evidence;
    }

    /**
     * Added for Phase 8 (RemediationVerificationService): runs the exact same
     * "5xx_rate" PromQL template used above for RCA evidence collection, so
     * remediation verification never duplicates or drifts from this query.
     * Public (rather than changing queryScalar's own visibility) to keep this
     * class's internal query plumbing private.
     */
    public Optional<Double> query5xxRate(String serviceSlug, Duration window) {

        long seconds = Math.max(60, Math.min(window.getSeconds(), 1800));

        String template = QUERIES.stream()
                .filter(q -> "5xx_rate".equals(q.name()))
                .findFirst()
                .map(NamedQuery::promqlTemplate)
                .orElseThrow(() -> new IllegalStateException("5xx_rate query template not found"));

        String promql = String.format(template, serviceSlug, serviceSlug, seconds);

        return queryScalar(promql);
    }

    private Optional<Double> queryScalar(String promql) {

        try {

            String response = prometheusRestClient.get()
                .uri("/api/v1/query?query={query}", promql)
                .retrieve()
                .body(String.class);

            JsonNode root = objectMapper.readTree(response);

            JsonNode result =
                    root.path("data").path("result");

            if (!result.isArray() || result.isEmpty()) {
                return Optional.empty();
            }

            JsonNode valueArray =
                    result.get(0).path("value");

            if (!valueArray.isArray() ||
                    valueArray.size() < 2) {

                return Optional.empty();
            }

            String raw =
                    valueArray.get(1).asText();

            if ("NaN".equalsIgnoreCase(raw)) {
                return Optional.empty();
            }

            return Optional.of(
                    Double.parseDouble(raw)
            );

        } catch (Exception e) {

            log.warn(
                    "Prometheus instant query failed: {} - query: {}",
                    e.getMessage(),
                    promql
            );

            return Optional.empty();
        }
    }

    private Evidence buildEvidence(
            EvidenceCollectionContext context,
            String title,
            String observedValue,
            String unit,
            String promql
    ) {

        String metadataJson;

        try {

            metadataJson =
                    objectMapper.writeValueAsString(
                            java.util.Map.of(
                                    "promql",
                                    promql,
                                    "unit",
                                    unit
                            )
                    );

        } catch (Exception e) {

            metadataJson = null;
        }

        return Evidence.builder()
                .investigationId(
                        context.investigationId()
                )
                .type(EvidenceType.METRIC)
                .source("prometheus")
                .observedAt(
                        context.windowEnd()
                )
                .title(
                        title +
                        " for " +
                        context.serviceSlug()
                )
                .description(
                        title +
                        " observed over the incident window (worst value)"
                )
                .observedValue(observedValue)
                .metadata(metadataJson)
                .build();
    }

    private String formatValue(
            double value,
            String unit
    ) {

        return switch (unit) {

            case "ratio" ->
                    String.format("%.4f", value);

            case "bytes" ->
                    String.format("%.0f", value);

            default ->
                    String.valueOf(value);
        };
    }

    private String boundedWindowSpec(
            Instant start,
            Instant end
    ) {

        long seconds =
                Duration.between(start, end).getSeconds();

        long clamped =
                Math.max(
                        120,
                        Math.min(seconds, 1800)
                );

        return clamped + "s";
    }
}