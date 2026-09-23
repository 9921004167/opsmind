package com.opsmind.core.incident;

import com.opsmind.core.incident.dto.*;
import com.opsmind.core.tenant.TenantContext;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping
    public List<IncidentResponse> list(@AuthenticationPrincipal TenantContext ctx) {
        return incidentService.listIncidents(ctx.organizationId());
    }

    @GetMapping("/{id}")
    public IncidentResponse get(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID id) {
        return incidentService.getIncident(ctx.organizationId(), id);
    }

    @GetMapping("/{id}/timeline")
    public List<IncidentTimelineEntryResponse> timeline(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID id) {
        return incidentService.getTimeline(ctx.organizationId(), id);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','SRE','ENGINEER')")
    public IncidentResponse updateStatus(@AuthenticationPrincipal TenantContext ctx,
                                          @PathVariable UUID id,
                                          @Valid @RequestBody IncidentStatusUpdateRequest request) {
        return incidentService.updateStatus(ctx.organizationId(), id, request, ctx.userId().toString());
    }
}
