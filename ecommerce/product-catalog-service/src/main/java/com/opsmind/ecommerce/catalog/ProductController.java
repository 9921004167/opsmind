package com.opsmind.ecommerce.catalog;

import com.opsmind.ecommerce.catalog.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Public product catalog - no auth in Phase 2. This mirrors a real storefront's
 * public browse API; account/checkout-level auth is deferred (see ARCHITECTURE.md).
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductRepository productRepository;

    @GetMapping
    public List<Product> list() {
        return productRepository.findByActiveTrue();
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }
}
