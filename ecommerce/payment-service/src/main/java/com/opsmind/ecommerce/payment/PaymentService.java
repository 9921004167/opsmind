package com.opsmind.ecommerce.payment;

import com.opsmind.ecommerce.payment.dto.ChargeRequest;
import com.opsmind.ecommerce.payment.dto.ChargeResponse;
import com.opsmind.ecommerce.payment.fault.PaymentFaultState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * There is no real payment gateway integration in Phase 2 - this simulates a
 * gateway call (a sleep representing network latency, then a decision), which is
 * standard practice for a reference/test application. What is real: the Payment
 * row persisted, the HTTP contract, and the fault-injection behavior actually
 * changing what this method does (not just what it logs).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final PaymentFaultState faultState;

    @Transactional
    public ChargeResponse charge(ChargeRequest request) {
        long latency = faultState.getLatencyMillis();
        if (latency > 0) {
            try {
                Thread.sleep(latency);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (faultState.isForce500Enabled()) {
            Payment failed = Payment.builder()
                    .orderId(request.orderId())
                    .customerId(request.customerId())
                    .amount(request.amount())
                    .status(PaymentStatus.FAILED)
                    .failureReason("fault_injected_500")
                    .build();
            paymentRepository.save(failed);
            log.error("Payment charge failed for order {} due to injected fault (force500)", request.orderId());
            throw new PaymentProcessingException("Payment gateway returned an error (fault injected)");
        }

        Payment payment = Payment.builder()
                .orderId(request.orderId())
                .customerId(request.customerId())
                .amount(request.amount())
                .status(PaymentStatus.SUCCEEDED)
                .build();
        payment = paymentRepository.save(payment);

        return new ChargeResponse(payment.getId(), payment.getOrderId(), payment.getStatus(),
                payment.getAmount(), payment.getFailureReason(), payment.getCreatedAt());
    }
}
