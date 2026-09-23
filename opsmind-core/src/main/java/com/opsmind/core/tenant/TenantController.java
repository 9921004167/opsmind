package com.opsmind.core.tenant;

import com.opsmind.core.tenant.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TenantController {

    private final TenantService tenantService;

    @PostMapping("/projects")
    @PreAuthorize("hasAnyRole('ADMIN','SRE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse createProject(@AuthenticationPrincipal TenantContext ctx,
                                          @Valid @RequestBody CreateProjectRequest request) {
        return tenantService.createProject(ctx.organizationId(), request);
    }

    @GetMapping("/projects")
    public List<ProjectResponse> listProjects(@AuthenticationPrincipal TenantContext ctx) {
        return tenantService.listProjects(ctx.organizationId());
    }

    @PostMapping("/projects/{projectId}/environments")
    @PreAuthorize("hasAnyRole('ADMIN','SRE')")
    @ResponseStatus(HttpStatus.CREATED)
    public EnvironmentResponse createEnvironment(@AuthenticationPrincipal TenantContext ctx,
                                                  @PathVariable UUID projectId,
                                                  @Valid @RequestBody CreateEnvironmentRequest request) {
        return tenantService.createEnvironment(ctx.organizationId(), projectId, request);
    }

    @GetMapping("/projects/{projectId}/environments")
    public List<EnvironmentResponse> listEnvironments(@AuthenticationPrincipal TenantContext ctx,
                                                        @PathVariable UUID projectId) {
        return tenantService.listEnvironments(ctx.organizationId(), projectId);
    }

    @PostMapping("/projects/{projectId}/services")
    @PreAuthorize("hasAnyRole('ADMIN','SRE')")
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse createService(@AuthenticationPrincipal TenantContext ctx,
                                          @PathVariable UUID projectId,
                                          @Valid @RequestBody CreateServiceRequest request) {
        return tenantService.createService(ctx.organizationId(), projectId, request);
    }

    @GetMapping("/projects/{projectId}/services")
    public List<ServiceResponse> listServices(@AuthenticationPrincipal TenantContext ctx,
                                               @PathVariable UUID projectId) {
        return tenantService.listServices(ctx.organizationId(), projectId);
    }
}
