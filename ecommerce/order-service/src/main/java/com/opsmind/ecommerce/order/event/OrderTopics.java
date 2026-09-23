package com.opsmind.ecommerce.order.event;

public final class OrderTopics {
    public static final String ORDER_CREATED = "ecommerce.order.created";
    public static final String ORDER_CONFIRMED = "ecommerce.order.confirmed";
    public static final String ORDER_FAILED = "ecommerce.order.failed";
    private OrderTopics() {}
}
