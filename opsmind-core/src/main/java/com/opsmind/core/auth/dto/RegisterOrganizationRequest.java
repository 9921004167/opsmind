package com.opsmind.core.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registers a brand new Organization together with its first user, who is
 * automatically granted ADMIN. There is intentionally no "join an existing org"
 * flow yet - that requires an invitation system, which is out of scope for Phase 1.
 */
public record RegisterOrganizationRequest(
        @NotBlank String organizationName,
        @NotBlank @Pattern(regexp = "^[a-z0-9-]+$", message = "slug must be lowercase letters, numbers and hyphens") String organizationSlug,
        @NotBlank String fullName,
        @Email @NotBlank String email,
        @NotBlank @Size(min = 8, message = "password must be at least 8 characters") String password
) {}
