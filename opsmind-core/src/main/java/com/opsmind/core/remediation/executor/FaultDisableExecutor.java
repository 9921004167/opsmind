package com.opsmind.core.remediation.executor;

import com.opsmind.core.remediation.ExecutorType;
import com.opsmind.core.remediation.RunbookDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;

/**
 * The ONLY real infrastructure executor in the platform (Section 12): calls the
 * existing Phase 4 fault-disable endpoint on a runbook's fixed executionEndpoint,
 * authenticated with the same FAULT_ADMIN_TOKEN the ecommerce services already
 * expect. Does not construct or accept arbitrary commands - the URL comes only
 * from the closed RunbookDefinition catalog, never from AI text.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FaultDisableExecutor implements RemediationExecutor {

    private final RestClient remediationRestClient;

    @Value("${opsmind.remediation.fault-admin-token:}")
    private String faultAdminToken;

    @Override
    public ExecutorType type() {
        return ExecutorType.FAULT_DISABLE;
    }

    @Override
    public ExecutionResult execute(RunbookDefinition runbook) {
        if (faultAdminToken == null || faultAdminToken.isBlank()) {
            return new ExecutionResult(false, "fault_admin_token_not_configured");
        }
        String endpoint = runbook.getExecutionEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            // Defensive: a FAULT_DISABLE runbook must always carry an endpoint.
            // If it doesn't, treat it as a configuration error, never guess one.
            return new ExecutionResult(false, "runbook_missing_execution_endpoint: " + runbook.getKey());
        }
        try {
            remediationRestClient.post()
                    .uri(URI.create(endpoint))
                    .header("X-Fault-Admin-Token", faultAdminToken)
                    .retrieve()
                    .toBodilessEntity();
            return new ExecutionResult(true, "fault-disable call succeeded: " + endpoint);
        } catch (Exception e) {
            log.error("Fault-disable execution failed for runbook {} at {}: {}", runbook.getKey(), endpoint, e.getMessage(), e);
            return new ExecutionResult(false, "fault_disable_call_failed: " + e.getMessage());
        }
    }
}
