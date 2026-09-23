package com.opsmind.ecommerce.inventory.dto;

import java.util.UUID;

public record StockResponse(UUID productId, int availableQuantity, boolean fromCache) {}
