package com.opsmind.ecommerce.order.client;

public class InventoryServiceUnavailableException extends RuntimeException {
    public InventoryServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
