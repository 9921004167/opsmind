package com.opsmind.core.remediation.ai;

import com.opsmind.core.investigation.RcaFinding;
import com.opsmind.core.remediation.RunbookDefinition;

import java.util.List;

/**
 * The ONLY thing an implementation may do is pick a runbook key from the closed
 * catalog it is given, or return none. Section 8: there is no code path anywhere
 * that turns freeTextRecommendation into a shell command or infrastructure
 * action - this interface's return type (RemediationAdvice) has no field that
 * could be executed, by construction.
 */
public interface RemediationAdvisor {

    RemediationAdvice recommend(IncidentSummary incidentSummary, RcaFinding rcaFinding, List<RunbookDefinition> catalog)
            throws RemediationAdvisorException;

    String providerName();

    String modelName();

    record IncidentSummary(String incidentNumber, String title, String severity, String affectedServiceSlug) {}
}
