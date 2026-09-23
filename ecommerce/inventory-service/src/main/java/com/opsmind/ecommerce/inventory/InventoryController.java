package com.opsmind.ecommerce.inventory;

import com.opsmind.ecommerce.inventory.dto.ReserveRequest;
import com.opsmind.ecommerce.inventory.dto.ReserveResponse;
import com.opsmind.ecommerce.inventory.dto.StockResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
public class InventoryController {

    private final InventoryService inventoryService;

    @GetMapping("/{productId}")
    public StockResponse getStock(@PathVariable UUID productId) {
        return inventoryService.getStock(productId);
    }

    @PostMapping("/reserve")
    public ReserveResponse reserve(@Valid @RequestBody ReserveRequest request) {
        return inventoryService.reserve(request);
    }

    /** Sets/replaces the available stock for a product. Used for warehouse stock
     *  updates in general, and specifically for seeding the demo catalog's inventory
     *  after product-catalog-service creates its products (see README). */
    @PutMapping("/{productId}/stock")
    public void setStock(@PathVariable UUID productId, @RequestBody StockUpdateRequest request) {
        inventoryService.setStock(productId, request.availableQuantity());
    }

    public record StockUpdateRequest(int availableQuantity) {}
}
