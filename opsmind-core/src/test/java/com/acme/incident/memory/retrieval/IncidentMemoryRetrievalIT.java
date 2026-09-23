package com.acme.incident.memory.retrieval;

import com.acme.incident.memory.port.ActiveIncidentView;
import com.acme.incident.memory.port.ResolvedIncidentView;
import com.opsmind.core.OpsMindCoreApplication;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = OpsMindCoreApplication.class)
class IncidentMemoryRetrievalIT {

    @Autowired
    private IncidentMemoryRetrievalService retrievalService;

    @Test
    void findsInc18ForASimilarNewIncident() {
        UUID organizationId = UUID.fromString("d72e3a82-24d7-4e4a-834b-0f068d3fdd85");

        ActiveIncidentView active = new ActiveIncidentView(
                UUID.fromString("6a986229-3355-461c-be27-bccf07dca8d1"),  // INC-000022, real row
                organizationId,
                "Order service elevated 5xx error rate",
                "CRITICAL",
                List.of("order-service"),
                Instant.now(),
                List.of(new ResolvedIncidentView.Symptom(
                        "prometheus", "high_5xx_rate", "order-service returning elevated 5xx")),
                List.of("Request rate approximately 0.20 req/s", "Service availability up"));

        List<HistoricalIncidentMatch> matches = retrievalService.findSimilar(active);

        System.out.println("=== RETRIEVED MATCHES: " + matches.size() + " ===");
        for (HistoricalIncidentMatch m : matches) {
            System.out.println("incident=" + m.incidentId()
                    + " similarity=" + m.similarity()
                    + " finalScore=" + m.finalScore());
        }

        boolean found = matches.stream()
                .anyMatch(m -> m.incidentId().equals(
                        UUID.fromString("64fd01a7-b193-478a-a419-ef6d94d3e376")));

        System.out.println("=== INC-000018 RETRIEVED: " + found + " ===");
    }
}