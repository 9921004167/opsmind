package com.opsmind.core.investigation.ai;

import com.opsmind.core.investigation.Evidence;

import java.util.List;

/**
 * The ONLY thing an implementation may do is reason over the evidence it's given
 * and return a structured hypothesis. No implementation may execute commands,
 * call remediation, or access anything beyond the evidence list and incident
 * summary passed in - that boundary is enforced by this interface's signature
 * itself (it has no way to reach anything else).
 */
public interface AIInvestigator {

    RcaResult investigate(IncidentSummary incidentSummary, List<Evidence> evidence) throws AIInvestigationException;

    String providerName();

    String modelName();

    record IncidentSummary(String incidentNumber, String title, String severity, String affectedServiceSlug) {}
}
