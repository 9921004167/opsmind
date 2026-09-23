package com.opsmind.core.tenant.dto;

import com.opsmind.core.tenant.EnvironmentType;

import java.time.Instant;
import java.util.UUID;

public record EnvironmentResponse(UUID id, UUID projectId, EnvironmentType type, Instant createdAt) {}
