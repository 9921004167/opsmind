package com.opsmind.ecommerce.payment.dto;

import com.opsmind.ecommerce.payment.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ChargeResponse(
        UUID paymentId,
        UUID orderId,
        PaymentStatus status,
        BigDecimal amount,
        String failureReason,
        Instant createdAt
) {}
