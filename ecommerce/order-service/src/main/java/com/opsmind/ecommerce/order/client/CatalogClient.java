package com.opsmind.ecommerce.order.client;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Real HTTP call to product-catalog-service. Order-service does NOT trust a
 * client-supplied price - it fetches the authoritative price at order time, the
 * way a real checkout must (Section 3: "must actually work end-to-end").
 */
@Component
@RequiredArgsConstructor
public class CatalogClient {

    private final RestClient catalogRestClient;

    public ProductInfo getProduct(UUID productId) {
        try {
            return catalogRestClient.get()
                    .uri("/api/products/{id}", productId)
                    .retrieve()
                    .body(ProductInfo.class);
        } catch (RestClientException e) {
            throw new CatalogUnavailableException("Could not fetch product " + productId + " from catalog service", e);
        }
    }

    public record ProductInfo(UUID id, String name, BigDecimal price, boolean active) {}
}
