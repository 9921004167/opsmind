package com.opsmind.ecommerce.order.fault;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Only "database latency" is simulated in-process for order-service (spec Section
 * 4C). "Service crash" is best tested by actually stopping the container
 * (`docker compose stop order-service`) - a real failure, not a fake one. Kafka
 * publish failure can be tested for real by stopping the kafka container.
 */
@Component
public class OrderFaultState {
    private final AtomicLong dbLatencyMillis = new AtomicLong(0);

    public void setDbLatencyMillis(long millis) { dbLatencyMillis.set(Math.max(0, millis)); }
    public void disableAll() { dbLatencyMillis.set(0); }
    public long getDbLatencyMillis() { return dbLatencyMillis.get(); }
}
