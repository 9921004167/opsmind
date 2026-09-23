package com.opsmind.core.remediation;

import com.opsmind.core.remediation.dto.*;
import com.opsmind.core.remediation.service.RemediationApprovalService;
import com.opsmind.core.remediation.service.RemediationExecutionService;
import com.opsmind.core.remediation.service.RemediationRecommendationService;
import com.opsmind.core.remediation.service.RunbookCatalogService;
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
public class RemediationController {

    private final RunbookCatalogService runbookCatalogService;
    private final RemediationRecommendationService recommendationService;
    private final RemediationApprovalService approvalService;
    private final RemediationExecutionService executionService;

    @GetMapping("/api/runbooks")
    public List<RunbookResponse> listRunbooks() {
        return runbookCatalogService.listAll();
    }

    @PostMapping("/api/incidents/{incidentId}/remediation-recommendations")
    @PreAuthorize("hasAnyRole('ADMIN','SRE','ENGINEER')")
    @ResponseStatus(HttpStatus.CREATED)
    public RemediationRecommendationResponse create(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID incidentId) {
        return recommendationService.create(ctx.organizationId(), incidentId, ctx.userId());
    }

    @GetMapping("/api/incidents/{incidentId}/remediation-recommendations")
    public List<RemediationRecommendationResponse> listForIncident(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID incidentId) {
        return recommendationService.listForIncident(ctx.organizationId(), incidentId);
    }

    @GetMapping("/api/remediation-recommendations/{id}")
    public RemediationRecommendationResponse get(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID id) {
        return recommendationService.get(ctx.organizationId(), id);
    }

    @PostMapping("/api/remediation-recommendations/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','SRE')")
    public RemediationRecommendationResponse approve(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID id,
                                                       @RequestBody(required = false) RejectRequest body) {
        String reason = body != null ? body.reason() : null;
        return approvalService.approve(ctx.organizationId(), id, ctx.userId(), reason, recommendationService::toResponse);
    }

    @PostMapping("/api/remediation-recommendations/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','SRE')")
    public RemediationRecommendationResponse reject(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID id,
                                                      @RequestBody(required = false) RejectRequest body) {
        String reason = body != null ? body.reason() : null;
        return approvalService.reject(ctx.organizationId(), id, ctx.userId(), reason, recommendationService::toResponse);
    }

    @PostMapping("/api/remediation-recommendations/{id}/execute")
    @PreAuthorize("hasAnyRole('ADMIN','SRE')")
    public RemediationRecommendationResponse execute(@AuthenticationPrincipal TenantContext ctx, @PathVariable UUID id) {
        return executionService.execute(ctx.organizationId(), id, ctx.userId(), recommendationService::toResponse);
    }
}
