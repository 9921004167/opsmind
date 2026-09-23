package com.acme.incident.memory.evidence;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Renders retrieved incidents into the block that goes into the Gemini RCA
 * prompt, alongside the live evidence you already assemble in Phase 6.
 *
 * The framing text is not decoration. Without an explicit instruction the
 * model treats retrieved incidents as established fact about the current
 * outage and will assert "this is the same failure as INC-1042". Priors,
 * not conclusions.
 */
@Component
public class HistoricalContextRenderer {

    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC);

    private static final String PREAMBLE = """
            ## ORGANIZATIONAL INCIDENT MEMORY

            The following are PAST incidents from this organization that a
            similarity search matched against the current one. They are prior
            evidence, not findings about the active incident.

            Rules for using this section:
            - Do not assume the current incident has the same root cause.
              Say what the current evidence does or does not support.
            - Cite a past incident by ID when you rely on it, and say which
              observation in the CURRENT evidence corroborates it.
            - A high similarity score means the write-ups read alike. It is
              not proof of a shared cause.
            - If none of these fit the current evidence, ignore them and say
              so briefly.
            """;

    public String render(List<HistoricalIncidentEvidence> history) {
        if (history == null || history.isEmpty()) {
            return """
                   ## ORGANIZATIONAL INCIDENT MEMORY

                   No sufficiently similar past incidents were found. Base the
                   analysis entirely on the current evidence.
                   """;
        }

        StringBuilder sb = new StringBuilder(PREAMBLE).append('\n');

        int i = 1;
        for (HistoricalIncidentEvidence h : history) {
            sb.append("### Past incident ").append(i++)
              .append(" — ID ").append(h.historicalIncidentId())
              .append(" (similarity ").append(String.format("%.2f", h.similarity())).append(")\n");
            sb.append("Resolved: ").append(DATE.format(h.resolvedAt()))
              .append("  |  Severity: ").append(h.severity())
              .append("  |  Services: ").append(String.join(", ", h.affectedServices()))
              .append('\n');
            sb.append("Title: ").append(h.title()).append('\n');
            sb.append("Root cause: ").append(h.rootCause()).append('\n');
            sb.append("Resolution: ").append(h.resolution()).append('\n');
            if (h.recommendations() != null && !h.recommendations().isEmpty()) {
                sb.append("Recommendations made at the time:\n");
                h.recommendations().forEach(r -> sb.append("  - ").append(r).append('\n'));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
