package com.opsmind.core.monitoring;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.*;

/**
 * Read-only, authenticated summary of real Prometheus instant queries for the four e-commerce jobs.
 * Any value Prometheus cannot supply is null; if Prometheus is unreachable, available=false. Nothing is estimated.
 */
@RestController
@RequestMapping("/api/monitoring")
@Slf4j
public class MonitoringController {
    private static final List<String> JOBS = List.of("order-service", "payment-service", "inventory-service", "product-catalog-service");
    private final RestClient prom;
    private final ObjectMapper mapper;

    public MonitoringController(@Qualifier("prometheusRestClient") RestClient prom, ObjectMapper mapper) {
        this.prom = prom;
        this.mapper = mapper;
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("source", "prometheus");
        out.put("asOf", Instant.now().toString());
        try {
            List<Map<String, Object>> services = new ArrayList<>();
            for (String job : JOBS) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("job", job);
                s.put("up", scalar("up{job=\"" + job + "\"}"));
                s.put("errorRatio", scalar("sum(rate(http_server_requests_seconds_count{job=\"" + job + "\",status=~\"5..\"}[1m])) / sum(rate(http_server_requests_seconds_count{job=\"" + job + "\"}[1m]))"));
                s.put("p95Seconds", scalar("histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket{job=\"" + job + "\"}[1m])) by (le))"));
                s.put("requestsPerSecond", scalar("sum(rate(http_server_requests_seconds_count{job=\"" + job + "\"}[1m]))"));
                services.add(s);
            }
            out.put("available", true);
            out.put("services", services);
        } catch (Exception e) {
            log.warn("Prometheus summary unavailable: {}", e.getMessage());
            out.put("available", false);
            out.put("services", List.of());
        }
        return out;
    }

    private Double scalar(String promql) throws Exception {
        String body = prom.get().uri("/api/v1/query?query={q}", promql).retrieve().body(String.class);
        JsonNode result = mapper.readTree(body).path("data").path("result");
        if (!result.isArray() || result.isEmpty()) return null;
        String v = result.get(0).path("value").path(1).asText("NaN");
        double d = Double.parseDouble(v);
        return Double.isNaN(d) || Double.isInfinite(d) ? null : d;
    }
}
