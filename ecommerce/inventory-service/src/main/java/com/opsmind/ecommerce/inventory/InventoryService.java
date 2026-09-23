package com.opsmind.ecommerce.inventory;

import com.opsmind.ecommerce.inventory.dto.ReserveRequest;
import com.opsmind.ecommerce.inventory.dto.ReserveResponse;
import com.opsmind.ecommerce.inventory.dto.StockResponse;
import com.opsmind.ecommerce.inventory.fault.InventoryFaultState;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryCacheService cacheService;
    private final InventoryFaultState faultState;

    public StockResponse getStock(UUID productId) {
        applyLatencyIfInjected();

        Integer cached = cacheService.getCached(productId); // throws if Redis fault injected - not caught here on purpose
        if (cached != null) {
            return new StockResponse(productId, cached, true);
        }

        InventoryItem item = inventoryItemRepository.findById(productId)
                .orElseThrow(() -> new NotFoundException("No inventory record for product: " + productId));
        cacheService.put(productId, item.getAvailableQuantity());
        return new StockResponse(productId, item.getAvailableQuantity(), false);
    }

    /**
     * Reservation intentionally bypasses Redis entirely and goes straight to Postgres
     * with optimistic locking (InventoryItem.version) - stock correctness must not
     * depend on cache freshness. Redis unavailability therefore never blocks a real
     * order from reserving stock; it only affects the read-through stock display.
     */
    @Transactional
    public ReserveResponse reserve(ReserveRequest request) {
        applyLatencyIfInjected();

        for (ReserveRequest.Item requestedItem : request.items()) {
            InventoryItem item = inventoryItemRepository.findById(requestedItem.productId())
                    .orElseThrow(() -> new NotFoundException("No inventory record for product: " + requestedItem.productId()));

            if (item.getAvailableQuantity() < requestedItem.quantity()) {
                return new ReserveResponse(request.orderId(), false,
                        "insufficient_stock:" + requestedItem.productId());
            }
        }

        // Second pass: all items had enough stock as of the check above, so apply
        // the reservation. Under concurrent requests, @Version optimistic locking
        // on InventoryItem will cause a conflicting concurrent reservation to fail
        // its transaction rather than silently oversell.
        for (ReserveRequest.Item requestedItem : request.items()) {
            InventoryItem item = inventoryItemRepository.findById(requestedItem.productId()).orElseThrow();
            item.setAvailableQuantity(item.getAvailableQuantity() - requestedItem.quantity());
            item.setReservedQuantity(item.getReservedQuantity() + requestedItem.quantity());
            inventoryItemRepository.save(item);
            cacheService.put(item.getProductId(), item.getAvailableQuantity());
        }

        return new ReserveResponse(request.orderId(), true, null);
    }

    /**
     * Upserts the available quantity for a product. This is a genuine stock-
     * management operation a real warehouse/ops integration would call - it is also
     * how the demo catalog's inventory gets seeded, since product-catalog-service's
     * seed migration generates product UUIDs at runtime that this service cannot
     * know about at its own migration-write time (see V2__comment.sql).
     */
    @Transactional
    public void setStock(java.util.UUID productId, int availableQuantity) {
        InventoryItem item = inventoryItemRepository.findById(productId)
                .orElse(InventoryItem.builder().productId(productId).availableQuantity(0).reservedQuantity(0).build());
        item.setAvailableQuantity(availableQuantity);
        inventoryItemRepository.save(item);
        cacheService.put(productId, availableQuantity);
    }

    private void applyLatencyIfInjected() {
        long latency = faultState.getDbLatencyMillis();
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
