package com.opsmind.core.investigation;

import com.opsmind.core.investigation.dto.InvestigationResponse;
import com.opsmind.core.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class InvestigationController {

    private final InvestigationService investigationService;

    @PostMapping("/api/incidents/{incidentId}/investigations")
    @PreAuthorize("hasAnyRole('ADMIN','SRE','ENGINEER')")
    @ResponseStatus(HttpStatus.CREATED)
    public InvestigationResponse create(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID incidentId) {
        return investigationService.createAndRun(ctx.organizationId(), incidentId, ctx.userId());
    }

    @GetMapping("/api/incidents/{incidentId}/investigations")
    public List<InvestigationResponse> listForIncident(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID incidentId) {
        return investigationService.listForIncident(ctx.organizationId(), incidentId);
    }

    @GetMapping("/api/investigations/{investigationId}")
    public InvestigationResponse get(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID investigationId) {
        return investigationService.get(ctx.organizationId(), investigationId);
    }

    @PostMapping("/api/investigations/{investigationId}/run")
    @PreAuthorize("hasAnyRole('ADMIN','SRE','ENGINEER')")
    public InvestigationResponse rerun(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID investigationId) {
        return investigationService.rerun(ctx.organizationId(), investigationId, ctx.userId());
    }
}
