package com.opsmind.ecommerce.inventory.fault;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/faults/inventory")
@RequiredArgsConstructor
public class FaultController {

    private final InventoryFaultState faultState;

    @Value("${opsmind.fault.admin-token}")
    private String adminToken;

    @PostMapping("/redis-failure")
    public ResponseEntity<?> redisFailure(@RequestHeader("X-Fault-Admin-Token") String token) {
        if (!requireAuthorized(token)) return unauthorized();
        faultState.enableRedisFailure();
        return ResponseEntity.ok(Map.of("redisFailure", true));
    }

    @PostMapping("/db-latency")
    public ResponseEntity<?> dbLatency(@RequestHeader("X-Fault-Admin-Token") String token,
                                        @RequestParam(defaultValue = "2000") long ms) {
        if (!requireAuthorized(token)) return unauthorized();
        faultState.setDbLatencyMillis(ms);
        return ResponseEntity.ok(Map.of("dbLatencyMillis", ms));
    }

    @PostMapping("/disable")
    public ResponseEntity<?> disable(@RequestHeader("X-Fault-Admin-Token") String token) {
        if (!requireAuthorized(token)) return unauthorized();
        faultState.disableAll();
        return ResponseEntity.ok(Map.of("redisFailure", false, "dbLatencyMillis", 0));
    }

    @GetMapping("/status")
    public ResponseEntity<?> status(@RequestHeader("X-Fault-Admin-Token") String token) {
        if (!requireAuthorized(token)) return unauthorized();
        return ResponseEntity.ok(Map.of(
                "redisFailure", faultState.isRedisFailureEnabled(),
                "dbLatencyMillis", faultState.getDbLatencyMillis()));
    }

    private boolean requireAuthorized(String token) {
        return adminToken != null && !adminToken.isBlank() && adminToken.equals(token);
    }

    private ResponseEntity<?> unauthorized() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "FORBIDDEN", "message", "Invalid or missing X-Fault-Admin-Token"));
    }
}
