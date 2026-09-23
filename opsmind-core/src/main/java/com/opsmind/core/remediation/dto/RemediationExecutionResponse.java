package com.opsmind.core.remediation.dto;

import com.opsmind.core.remediation.ExecutorType;

import java.time.Instant;
import java.util.UUID;

public record RemediationExecutionResponse(
        UUID id, ExecutorType executorType, boolean succeeded, String detail,
        UUID actorUserId, Instant startedAt, Instant completedAt) {}
