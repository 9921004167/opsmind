package com.opsmind.ecommerce.inventory.fault;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fault injection toggles (spec Section 4B). "Database unavailable" is deliberately
 * NOT simulated by a toggle here - the most realistic way to test that is to
 * actually stop the postgres container (`docker compose stop ecommerce-db`), which
 * is a genuine failure rather than an in-process fake. This class covers the two
 * faults that are meaningfully simulatable in-process: Redis being unreachable, and
 * slow responses.
 */
@Component
public class InventoryFaultState {

    private final AtomicBoolean redisFailure = new AtomicBoolean(false);
    private final AtomicLong dbLatencyMillis = new AtomicLong(0);

    public void enableRedisFailure() { redisFailure.set(true); }
    public void setDbLatencyMillis(long millis) { dbLatencyMillis.set(Math.max(0, millis)); }
    public void disableAll() { redisFailure.set(false); dbLatencyMillis.set(0); }
    public boolean isRedisFailureEnabled() { return redisFailure.get(); }
    public long getDbLatencyMillis() { return dbLatencyMillis.get(); }
}
