package com.opsmind.core.remediation.executor;

import com.opsmind.core.remediation.ExecutorType;
import com.opsmind.core.remediation.RunbookDefinition;
import org.springframework.stereotype.Component;

/**
 * Registered for completeness/defense-in-depth only. RemediationExecutionService
 * already refuses to execute anything flagged manualOnly before an executor is
 * ever selected (Section 8/12), so this should never actually be invoked in
 * normal operation - if it is, it fails safe rather than doing anything.
 */
@Component
public class ManualOnlyExecutor implements RemediationExecutor {

    @Override
    public ExecutorType type() {
        return ExecutorType.MANUAL;
    }

    @Override
    public ExecutionResult execute(RunbookDefinition runbook) {
        return new ExecutionResult(false,
                "manual_only: no automated executor exists for '" + runbook.getKey() + "'; a human must perform this action");
    }
}
