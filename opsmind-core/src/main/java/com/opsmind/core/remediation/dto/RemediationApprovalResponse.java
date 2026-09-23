package com.opsmind.core.remediation.dto;

import com.opsmind.core.remediation.ApprovalDecision;

import java.time.Instant;
import java.util.UUID;

public record RemediationApprovalResponse(
        UUID id, ApprovalDecision decision, UUID actorUserId, boolean autoApproved, String reason, Instant createdAt) {}
