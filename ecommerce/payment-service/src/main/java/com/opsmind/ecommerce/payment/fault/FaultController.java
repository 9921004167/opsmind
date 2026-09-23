package com.opsmind.ecommerce.payment.fault;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controlled failure injection endpoints (spec Section 4A). Protected by a shared
 * admin token so these cannot be hit anonymously in a shared staging environment -
 * this is a deliberately lightweight check for Phase 2; full RBAC-based protection
 * for fault injection is Phase 17 (security hardening) work.
 */
@RestController
@RequestMapping("/faults/payment")
@RequiredArgsConstructor
public class FaultController {

    private final PaymentFaultState faultState;

    @Value("${opsmind.fault.admin-token}")
    private String adminToken;

    @PostMapping("/enable-500")
    public ResponseEntity<?> enable500(@RequestHeader("X-Fault-Admin-Token") String token) {
        if (!requireAuthorized(token)) return unauthorized();
        faultState.enableForce500();
        return ResponseEntity.ok(Map.of("force500", true));
    }

    @PostMapping("/latency")
    public ResponseEntity<?> latency(@RequestHeader("X-Fault-Admin-Token") String token,
                                      @RequestParam(defaultValue = "2000") long ms) {
        if (!requireAuthorized(token)) return unauthorized();
        faultState.setLatencyMillis(ms);
        return ResponseEntity.ok(Map.of("latencyMillis", ms));
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestHeader("X-Fault-Admin-Token") String token) {
        if (!requireAuthorized(token)) return unauthorized();
        faultState.disableAll();
        return ResponseEntity.ok(Map.of("force500", false, "latencyMillis", 0));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestHeader("X-Fault-Admin-Token") String token) {
        if (!requireAuthorized(token)) return unauthorized();
        return ResponseEntity.ok(Map.of(
                "force500", faultState.isForce500Enabled(),
                "latencyMillis", faultState.getLatencyMillis()));
    }

    private boolean requireAuthorized(String token) {
        return adminToken != null && !adminToken.isBlank() && adminToken.equals(token);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "FORBIDDEN", "message", "Invalid or missing X-Fault-Admin-Token"));
    }
}
