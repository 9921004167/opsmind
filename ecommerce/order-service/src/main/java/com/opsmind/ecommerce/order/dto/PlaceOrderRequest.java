package com.opsmind.ecommerce.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record PlaceOrderRequest(
        @NotBlank String customerId,
        @NotEmpty @Valid List<Item> items
) {
    public record Item(@NotNull UUID productId, @Min(1) int quantity) {}
}
