package com.opsmind.ecommerce.inventory.dto;

import java.util.UUID;

public record ReserveResponse(UUID orderId, boolean reserved, String reason) {}
