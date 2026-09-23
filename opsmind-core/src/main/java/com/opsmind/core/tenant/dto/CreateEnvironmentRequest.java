package com.opsmind.core.tenant.dto;

import com.opsmind.core.tenant.EnvironmentType;
import jakarta.validation.constraints.NotNull;

public record CreateEnvironmentRequest(@NotNull EnvironmentType type) {}
