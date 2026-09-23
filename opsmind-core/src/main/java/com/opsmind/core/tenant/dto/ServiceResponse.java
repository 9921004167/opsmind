package com.opsmind.core.tenant.dto;

import java.time.Instant;
import java.util.UUID;

public record ServiceResponse(UUID id, UUID projectId, String name, String slug, String description, Instant createdAt) {}
