package com.opsmind.core.faultlab;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.opsmind.core.tenant.TenantContext;

import java.util.*;

@RestController
@RequestMapping("/api/fault-lab")
@PreAuthorize("hasAnyRole('ADMIN','SRE','ENGINEER')")
public class FaultLabController {
    public record StartRequest(String faultKey, Integer durationSeconds, Long latencyMs) {}

    private final FaultLabService service;
    public FaultLabController(FaultLabService service) { this.service = service; }

    @GetMapping("/capabilities") public Map<String, Object> capabilities(@AuthenticationPrincipal TenantContext c) { return service.capabilities(c); }
    @GetMapping("/experiments") public List<Map<String, Object>> list(@AuthenticationPrincipal TenantContext c) { return service.list(c); }
    @PostMapping("/experiments") public Map<String, Object> start(@AuthenticationPrincipal TenantContext c, @RequestBody StartRequest r) {
        return service.start(c, r.faultKey(), r.durationSeconds(), r.latencyMs()); }
    @PostMapping("/experiments/{id}/stop") public Map<String, Object> stop(@AuthenticationPrincipal TenantContext c, @PathVariable UUID id) { return service.stop(c, id); }
    @PostMapping("/emergency-stop") @PreAuthorize("hasAnyRole('ADMIN','SRE')") public Map<String, Object> emergency(@AuthenticationPrincipal TenantContext c) { return service.emergencyStop(c); }
}
