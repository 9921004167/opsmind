package com.opsmind.core.tenant.dto;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, UUID organizationId, String name, String slug, Instant createdAt) {}
