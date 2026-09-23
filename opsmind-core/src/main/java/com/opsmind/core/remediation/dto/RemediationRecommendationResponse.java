package com.opsmind.core.remediation.dto;

import com.opsmind.core.remediation.RecommendationStatus;
import com.opsmind.core.remediation.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RemediationRecommendationResponse(
        UUID id, UUID incidentId, UUID rcaFindingId,
        String matchedRunbookKey, String aiSuggestedKey,
        String aiRecommendationText, String aiRationale,
        RiskLevel riskLevel, boolean manualOnly, RecommendationStatus status,
        Instant createdAt, Instant updatedAt,
        List<RemediationApprovalResponse> approvals,
        List<RemediationExecutionResponse> executions,
        List<RemediationVerificationResponse> verifications) {}
