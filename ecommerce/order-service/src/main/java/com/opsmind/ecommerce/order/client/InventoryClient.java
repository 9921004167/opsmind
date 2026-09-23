package com.opsmind.ecommerce.order.client;

import com.opsmind.ecommerce.order.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InventoryClient {

    private final RestClient inventoryRestClient;

    public ReserveResult reserve(UUID orderId, List<OrderItem> items) {
        try {
            List<ReserveRequest.Item> reqItems = items.stream()
                    .map(i -> new ReserveRequest.Item(i.getProductId(), i.getQuantity()))
                    .toList();
            ReserveResponseBody body = inventoryRestClient.post()
                    .uri("/api/inventory/reserve")
                    .body(new ReserveRequest(orderId, reqItems))
                    .retrieve()
                    .body(ReserveResponseBody.class);
            return new ReserveResult(body != null && body.reserved(), body != null ? body.reason() : "empty_response");
        } catch (RestClientException e) {
            throw new InventoryServiceUnavailableException("Could not reach inventory service for order " + orderId, e);
        }
    }

    public record ReserveRequest(UUID orderId, List<Item> items) {
        public record Item(UUID productId, int quantity) {}
    }
    public record ReserveResponseBody(UUID orderId, boolean reserved, String reason) {}
    public record ReserveResult(boolean reserved, String reason) {}
}
