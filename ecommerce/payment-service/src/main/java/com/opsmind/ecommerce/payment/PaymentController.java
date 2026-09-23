package com.opsmind.ecommerce.payment;

import com.opsmind.ecommerce.payment.dto.ChargeRequest;
import com.opsmind.ecommerce.payment.dto.ChargeResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final PaymentRepository paymentRepository;

    @PostMapping("/charge")
    @ResponseStatus(HttpStatus.CREATED)
    public ChargeResponse charge(@Valid @RequestBody ChargeRequest request) {
        return paymentService.charge(request);
    }

    @GetMapping("/{id}")
    public Payment get(@PathVariable UUID id) {
        return paymentRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Payment not found: " + id));
    }
}
