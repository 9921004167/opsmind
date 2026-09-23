package com.opsmind.ecommerce.order.event;

import java.util.UUID;

public record OrderConfirmedEvent(UUID orderId, String customerId) {}
