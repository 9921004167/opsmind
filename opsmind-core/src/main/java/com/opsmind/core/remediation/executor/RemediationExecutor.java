package com.opsmind.core.remediation.executor;

import com.opsmind.core.remediation.ExecutorType;
import com.opsmind.core.remediation.RunbookDefinition;

/**
 * A real executor for one ExecutorType. Spring collects every implementation
 * (see RemediationExecutionService) and dispatches by RunbookDefinition.executorType -
 * there is no other way for a recommendation to reach infrastructure, and no
 * implementation of this interface may accept free-text AI output as its action;
 * it only ever receives a validated, catalog-backed RunbookDefinition.
 */
public interface RemediationExecutor {

    ExecutorType type();

    ExecutionResult execute(RunbookDefinition runbook);

    record ExecutionResult(boolean success, String detail) {}
}
