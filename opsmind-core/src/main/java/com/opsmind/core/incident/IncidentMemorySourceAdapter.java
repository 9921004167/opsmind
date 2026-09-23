package com.opsmind.core.incident;

import com.acme.incident.memory.port.ActiveIncidentView;
import com.acme.incident.memory.port.IncidentMemorySourcePort;
import com.acme.incident.memory.port.ResolvedIncidentView;
import com.opsmind.core.investigation.Evidence;
import com.opsmind.core.investigation.EvidenceRepository;
import com.opsmind.core.investigation.Investigation;
import com.opsmind.core.investigation.InvestigationRepository;
import com.opsmind.core.investigation.RcaFinding;
import com.opsmind.core.investigation.RcaFindingRepository;
import org.springframework.stereotype.Component;
import com.opsmind.core.tenant.MonitoredServiceRepository;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class IncidentMemorySourceAdapter implements IncidentMemorySourcePort {

    private final IncidentRepository incidentRepository;
    private final InvestigationRepository investigationRepository;
    private final RcaFindingRepository rcaFindingRepository;
    private final EvidenceRepository evidenceRepository;
    private final MonitoredServiceRepository monitoredServiceRepository;

    public IncidentMemorySourceAdapter(
            IncidentRepository incidentRepository,
            InvestigationRepository investigationRepository,
            RcaFindingRepository rcaFindingRepository,
            EvidenceRepository evidenceRepository,
            MonitoredServiceRepository monitoredServiceRepository) {

        this.incidentRepository = incidentRepository;
        this.investigationRepository = investigationRepository;
        this.rcaFindingRepository = rcaFindingRepository;
        this.evidenceRepository = evidenceRepository;
        this.monitoredServiceRepository = monitoredServiceRepository;
    }

    @Override
    public Optional<ResolvedIncidentView> loadResolved(
            UUID organizationId,
            UUID incidentId) {

        return incidentRepository
                .findByIdAndOrganizationId(incidentId, organizationId)
                .flatMap(incident -> {

                    if (incident.getResolvedAt() == null) {
                        return Optional.empty();
                    }

                    List<Investigation> investigations =
                            investigationRepository
                                    .findByIncidentIdOrderByCreatedAtDesc(incidentId);

                    for (Investigation investigation : investigations) {

                        if (!organizationId.equals(investigation.getOrganizationId())) {
                            continue;
                        }

                        Optional<RcaFinding> rca =
                                rcaFindingRepository
                                        .findByInvestigationId(investigation.getId());

                        if (rca.isEmpty()) {
                            continue;
                        }

                        RcaFinding finding = rca.get();

                        return Optional.of(new ResolvedIncidentView(
                                incident.getId(),
                                incident.getOrganizationId(),
                                incident.getTitle(),
                                incident.getSeverity().name(),
                                serviceNames(incident),
                                incident.getCreatedAt(),
                                incident.getResolvedAt(),
                                symptoms(investigation.getId()),
                                finding.getRootCause(),
                                asSingletonOrEmpty(finding.getHypothesis()),
                                incident.getSummary(),
                                parseJsonArray(finding.getRecommendedActions()),
                                finding.getConfidenceLevel() != null ? finding.getConfidenceLevel().name() : null,
                                finding.getConfidenceScore()
                        ));
                    }

                    return Optional.empty();
                });
    }

    @Override
    public Optional<ActiveIncidentView> loadActive(
            UUID organizationId,
            UUID incidentId) {

        return incidentRepository
                .findByIdAndOrganizationId(incidentId, organizationId)
                .map(incident -> {

                    List<Investigation> investigations =
                            investigationRepository
                                    .findByIncidentIdOrderByCreatedAtDesc(incidentId);

                    List<String> evidenceSummaries = investigations.stream()
                            .filter(i -> organizationId.equals(i.getOrganizationId()))
                            .findFirst()
                            .map(i -> evidenceRepository
                                    .findByInvestigationIdOrderByObservedAtAsc(i.getId())
                                    .stream()
                                    .map(this::evidenceSummary)
                                    .toList())
                            .orElse(Collections.emptyList());

                    return new ActiveIncidentView(
                            incident.getId(),
                            incident.getOrganizationId(),
                            incident.getTitle(),
                            incident.getSeverity().name(),
                            serviceNames(incident),
                            incident.getCreatedAt(),
                            Collections.emptyList(),
                            evidenceSummaries
                    );
                });
    }

    private List<String> serviceNames(Incident incident) {
        if (incident.getPrimaryServiceId() == null) {
            return Collections.emptyList();
        }

        return monitoredServiceRepository
                .findById(incident.getPrimaryServiceId())
                .map(service -> List.of(service.getSlug()))
                .orElse(Collections.emptyList());
    }

    private List<ResolvedIncidentView.Symptom> symptoms(UUID investigationId) {
        return evidenceRepository
                .findByInvestigationIdOrderByObservedAtAsc(investigationId)
                .stream()
                .map(e -> new ResolvedIncidentView.Symptom(
                        e.getSource(),
                        e.getType().name(),
                        e.getDescription() != null
                                ? e.getDescription()
                                : e.getTitle()))
                .toList();
    }

    private String evidenceSummary(Evidence evidence) {
        String value = evidence.getObservedValue();

        if (value == null || value.isBlank()) {
            return evidence.getTitle();
        }

        return evidence.getTitle() + ": " + value;
    }

    /**
     * hypothesis is free-text (a single interpretive sentence from Gemini), not a
     * JSON array - unlike recommendedActions, which genuinely is one. Wrapping it
     * as a singleton list here (rather than reusing parseJsonArray, which would
     * shred the sentence on every comma) keeps it intact as one contributing
     * factor rather than corrupting it into fragments.
     */
    private List<String> asSingletonOrEmpty(String value) {
        return (value == null || value.isBlank())
                ? Collections.emptyList()
                : List.of(value.trim());
    }

    private List<String> parseJsonArray(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }

        String cleaned = value
                .replace("[", "")
                .replace("]", "")
                .replace("\"", "");

        if (cleaned.isBlank()) {
            return Collections.emptyList();
        }

        return Arrays.stream(cleaned.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();
    }
}