package com.opsmind.core.remediation.dto;

import com.opsmind.core.remediation.VerificationResult;

import java.time.Instant;
import java.util.UUID;

public record RemediationVerificationResponse(
        UUID id, VerificationResult result, String metricName, String observedValue, String detail, Instant verifiedAt) {}
