package com.opsmind.ecommerce.order.dto;

import com.opsmind.ecommerce.order.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        String failureReason,
        Instant createdAt,
        Instant updatedAt,
        List<OrderItemResponse> items
) {
    public record OrderItemResponse(UUID productId, String productName, int quantity, BigDecimal unitPrice) {}
}
