package com.opsmind.core.faultlab;

import java.util.List;
import java.util.Optional;

/** Closed allow-list of faults the e-commerce services really implement. Nothing outside it can run. */
public final class FaultCatalog {
    public record Fault(String key, String service, String label, String path, boolean needsMs,
                        String expectedImpact) {}

    public static final long MIN_MS = 100, MAX_MS = 5000;

    public static final List<Fault> ALL = List.of(
        new Fault("payment.force-500", "payment", "Forced HTTP 500", "/faults/payment/enable-500", false,
            "Payment charges return 500; order failures and a rising 5xx rate are expected."),
        new Fault("payment.latency", "payment", "Injected latency", "/faults/payment/latency", true,
            "Payment responses are delayed; higher p95 latency and possible caller timeouts expected."),
        new Fault("inventory.redis-failure", "inventory", "Redis failure", "/faults/inventory/redis-failure", false,
            "Stock lookups lose their cache; degraded lookups or errors expected."),
        new Fault("inventory.db-latency", "inventory", "Database latency", "/faults/inventory/db-latency", true,
            "Inventory queries slow down; higher latency expected."),
        new Fault("order.db-latency", "order", "Database latency", "/faults/order/db-latency", true,
            "Order queries slow down; higher latency expected."));

    public static Optional<Fault> find(String key) {
        return ALL.stream().filter(f -> f.key().equals(key)).findFirst();
    }

    public static String disablePath(String service) { return "/faults/" + service + "/disable"; }

    private FaultCatalog() {}
}
