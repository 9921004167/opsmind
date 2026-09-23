package com.opsmind.ecommerce.payment.fault;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory fault injection toggles for the failure engine (spec Section 4).
 * These are REAL controls: when force500 is set, PaymentController genuinely
 * returns HTTP 500 instead of processing the charge - not a simulated log line.
 * State is process-local and resets on restart; that is acceptable for a
 * dev/staging fault-injection mechanism and is documented as such.
 */
@Component
public class PaymentFaultState {

    private final AtomicBoolean force500 = new AtomicBoolean(false);
    private final AtomicLong latencyMillis = new AtomicLong(0);

    public void enableForce500() {
        force500.set(true);
    }

    public void setLatencyMillis(long millis) {
        latencyMillis.set(Math.max(0, millis));
    }

    public void disableAll() {
        force500.set(false);
        latencyMillis.set(0);
    }

    public boolean isForce500Enabled() {
        return force500.get();
    }

    public long getLatencyMillis() {
        return latencyMillis.get();
    }
}
