package com.opsmind.ecommerce.order.event;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCreatedEvent(UUID orderId, String customerId, BigDecimal totalAmount) {}
