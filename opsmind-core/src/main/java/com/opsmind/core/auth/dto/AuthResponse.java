package com.opsmind.core.auth.dto;

import java.util.UUID;

public record AuthResponse(String accessToken, long expiresInSeconds, UUID userId, UUID organizationId, String role) {}
