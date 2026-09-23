package com.opsmind.ecommerce.payment;

/** Thrown when a charge cannot be processed - either genuinely, or via fault injection. */
public class PaymentProcessingException extends RuntimeException {
    public PaymentProcessingException(String message) {
        super(message);
    }
}
