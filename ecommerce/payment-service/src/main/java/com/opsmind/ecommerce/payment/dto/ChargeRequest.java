package com.opsmind.ecommerce.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record ChargeRequest(
        @NotNull UUID orderId,
        @NotBlank String customerId,
        @NotNull @DecimalMin(value = "0.01") BigDecimal amount
) {}
