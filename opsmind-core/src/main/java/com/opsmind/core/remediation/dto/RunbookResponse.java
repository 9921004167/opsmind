package com.opsmind.core.remediation.dto;

import com.opsmind.core.remediation.ExecutorType;
import com.opsmind.core.remediation.RiskLevel;

import java.util.UUID;

public record RunbookResponse(
        UUID id, String key, String title, String description,
        RiskLevel riskLevel, ExecutorType executorType, String targetServiceSlug) {}
