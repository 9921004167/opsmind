package com.opsmind.ecommerce.order.event;

import com.opsmind.ecommerce.order.OrderStatus;

import java.util.UUID;

public record OrderFailedEvent(UUID orderId, String customerId, OrderStatus failedAtStatus, String reason) {}
