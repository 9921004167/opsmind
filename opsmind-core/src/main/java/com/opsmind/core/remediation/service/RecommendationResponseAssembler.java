package com.opsmind.core.remediation.service;

import com.opsmind.core.remediation.RemediationRecommendation;
import com.opsmind.core.remediation.dto.RemediationRecommendationResponse;

/** Shared by RemediationApprovalService and RemediationExecutionService so both
 *  can reuse RemediationRecommendationService's full response assembly (with
 *  nested approvals/executions/verifications) without a circular bean dependency. */
@FunctionalInterface
public interface RecommendationResponseAssembler {
    RemediationRecommendationResponse toResponse(RemediationRecommendation recommendation);
}
