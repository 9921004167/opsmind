package com.opsmind.ecommerce.inventory;

import com.opsmind.ecommerce.inventory.fault.InventoryFaultState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

/**
 * Real cache-aside pattern in front of Postgres (source of truth for stock).
 * When InventoryFaultState.redisFailure is enabled, this throws immediately rather
 * than falling back silently to the DB - this class exists specifically to make a
 * "Redis unavailable" incident real and visible to OpsMind, not to demonstrate
 * resilience patterns (those are a legitimate future improvement, not Phase 2 scope).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryCacheService {

    private static final Duration TTL = Duration.ofSeconds(30);

    private final StringRedisTemplate redisTemplate;
    private final InventoryFaultState faultState;

    public Integer getCached(UUID productId) {
        if (faultState.isRedisFailureEnabled()) {
            throw new RedisUnavailableException("Redis connection failed (fault injected)");
        }
        String value = redisTemplate.opsForValue().get(cacheKey(productId));
        return value != null ? Integer.parseInt(value) : null;
    }

    public void put(UUID productId, int availableQuantity) {
        if (faultState.isRedisFailureEnabled()) {
            // Best-effort write path: do not fail the caller just because the cache
            // write couldn't happen - Postgres remains correct either way.
            log.warn("Skipping cache write for product {} - Redis fault injected", productId);
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey(productId), String.valueOf(availableQuantity), TTL);
        } catch (Exception e) {
            log.warn("Cache write failed for product {}: {}", productId, e.getMessage());
        }
    }

    private String cacheKey(UUID productId) {
        return "inventory:available:" + productId;
    }
}
