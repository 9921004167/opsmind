package com.opsmind.ecommerce.order.client;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class PaymentClient {

    private final RestClient paymentRestClient;

    /**
     * Never throws for a business-level payment failure (HTTP 500 from
     * payment-service, including fault-injected ones) - that is a real, expected
     * outcome the order flow must handle, not a bug. It DOES throw
     * PaymentServiceUnavailableException for genuine connectivity failure (DNS,
     * connection refused, timeout), since order-service can't distinguish "declined"
     * from "unreachable" any other way with a plain REST call.
     */
    public ChargeResult charge(UUID orderId, String customerId, BigDecimal amount) {
        try {
            return paymentRestClient.post()
                    .uri("/api/payments/charge")
                    .body(new ChargeRequest(orderId, customerId, amount))
                    .exchange((request, response) -> {
                        if (response.getStatusCode().is2xxSuccessful()) {
                            ChargeResponseBody body = response.bodyTo(ChargeResponseBody.class);
                            return new ChargeResult(true, body != null ? body.paymentId() : null, null);
                        }
                        String reason = describeFailure(response.getStatusCode());
                        return new ChargeResult(false, null, reason);
                    });
        } catch (RestClientException e) {
            throw new PaymentServiceUnavailableException("Could not reach payment service for order " + orderId, e);
        }
    }

    private String describeFailure(HttpStatusCode status) {
        return "payment_service_returned_" + status.value();
    }

    public record ChargeRequest(UUID orderId, String customerId, BigDecimal amount) {}
    public record ChargeResponseBody(UUID paymentId, UUID orderId, String status, BigDecimal amount, String failureReason, String createdAt) {}
    public record ChargeResult(boolean success, UUID paymentId, String failureReason) {}
}
