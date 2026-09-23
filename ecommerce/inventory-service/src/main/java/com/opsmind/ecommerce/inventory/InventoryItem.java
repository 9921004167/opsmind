package com.opsmind.ecommerce.inventory;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/** Postgres is the source of truth for stock. Redis (see InventoryCacheService) is a
 *  read-through cache in front of it - not a second source of truth. */
@Entity
@Table(name = "inventory_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventoryItem {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    private long version;
}
