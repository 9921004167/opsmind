package com.acme.incident.memory.document;

import com.acme.incident.memory.config.IncidentMemoryProperties;
import com.acme.incident.memory.port.ActiveIncidentView;
import com.acme.incident.memory.port.ResolvedIncidentView;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * Turns an incident into the text that gets embedded.
 *
 * This class, more than any tuning parameter, decides retrieval quality.
 *
 * What is deliberately EXCLUDED: timestamps, incident IDs, hostnames with
 * embedded dates, engineer names, trace IDs. They carry no semantic signal
 * and they drag unrelated incidents together because they share a format.
 *
 * Symptoms come first because the query side is dominated by symptoms: a
 * live incident has no root cause yet.
 */
@Component
public class IncidentMemoryDocumentBuilder {

    private final int maxChars;

    public IncidentMemoryDocumentBuilder(IncidentMemoryProperties props) {
        this.maxChars = props.embedding().maxDocumentChars();
    }

    /** Document side: what we store. */
    public String buildDocument(ResolvedIncidentView inc) {
        StringBuilder sb = new StringBuilder(2048);

        sb.append("TITLE: ").append(nz(inc.title())).append('\n');
        sb.append("SEVERITY: ").append(nz(inc.severity())).append('\n');
        sb.append("AFFECTED SERVICES: ").append(joinServices(inc.affectedServices())).append("\n\n");

        sb.append("SYMPTOMS OBSERVED:\n");
        appendSymptoms(sb, inc.symptoms());

        sb.append("\nROOT CAUSE:\n").append(nz(inc.rootCause())).append('\n');

        if (notEmpty(inc.contributingFactors())) {
            sb.append("\nCONTRIBUTING FACTORS:\n");
            inc.contributingFactors().forEach(f -> sb.append("- ").append(f).append('\n'));
        }

        sb.append("\nRESOLUTION:\n").append(nz(inc.resolutionSummary())).append('\n');

        if (notEmpty(inc.recommendations())) {
            sb.append("\nRECOMMENDATIONS:\n");
            inc.recommendations().forEach(r -> sb.append("- ").append(r).append('\n'));
        }

        return truncate(sb.toString());
    }

    /**
     * Query side: same vocabulary and section order as the document, minus
     * the sections a live incident cannot have. Matching the shape matters
     * more than matching the length.
     */
    public String buildQuery(ActiveIncidentView inc) {
        StringBuilder sb = new StringBuilder(1024);

        sb.append("TITLE: ").append(nz(inc.title())).append('\n');
        sb.append("SEVERITY: ").append(nz(inc.severity())).append('\n');
        sb.append("AFFECTED SERVICES: ").append(joinServices(inc.affectedServices())).append("\n\n");

        sb.append("SYMPTOMS OBSERVED:\n");
        appendSymptoms(sb, inc.symptoms());

        if (notEmpty(inc.evidenceSummaries())) {
            sb.append("\nEVIDENCE SO FAR:\n");
            inc.evidenceSummaries().forEach(e -> sb.append("- ").append(e).append('\n'));
        }

        return truncate(sb.toString());
    }

    private void appendSymptoms(StringBuilder sb, List<ResolvedIncidentView.Symptom> symptoms) {
        if (!notEmpty(symptoms)) {
            sb.append("- (none recorded)\n");
            return;
        }
        symptoms.stream()
                .map(s -> "- " + nz(s.source()) + " / " + nz(s.signal())
                        + (s.detail() == null || s.detail().isBlank() ? "" : ": " + s.detail()))
                .distinct()          // dedup fan-out from the phase 5 correlator
                .limit(40)
                .forEach(line -> sb.append(line).append('\n'));
    }

    private static String joinServices(List<String> services) {
        if (!notEmpty(services)) {
            return "(unknown)";
        }
        return services.stream()
                .filter(s -> s != null && !s.isBlank())
                .map(s -> s.toLowerCase(Locale.ROOT).trim())
                .distinct()
                .sorted()
                .reduce((a, b) -> a + ", " + b)
                .orElse("(unknown)");
    }

    private String truncate(String s) {
        return s.length() <= maxChars ? s : s.substring(0, maxChars);
    }

    private static boolean notEmpty(List<?> l) {
        return l != null && !l.isEmpty();
    }

    private static String nz(String s) {
        return s == null || s.isBlank() ? "(unknown)" : s.trim();
    }
}
