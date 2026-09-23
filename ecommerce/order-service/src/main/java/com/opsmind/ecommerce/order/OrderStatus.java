package com.opsmind.ecommerce.order;

/**
 * Simpler than OpsMind's own IncidentStatus - this is the monitored application's
 * own order lifecycle, not an incident lifecycle.
 *
 * CREATED -> PAID -> CONFIRMED           (happy path)
 * CREATED -> PAYMENT_FAILED              (terminal - payment declined/errored)
 * PAID -> INVENTORY_FAILED               (terminal - KNOWN GAP: payment is not
 *                                          automatically refunded in Phase 2;
 *                                          compensating-transaction/saga logic is
 *                                          not built yet)
 */
public enum OrderStatus {
    CREATED,
    PAID,
    PAYMENT_FAILED,
    CONFIRMED,
    INVENTORY_FAILED
}
