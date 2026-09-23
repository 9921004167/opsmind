package com.opsmind.core.tenant;

import com.opsmind.core.common.NotFoundException;
import com.opsmind.core.tenant.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class TenantService {

    private final ProjectRepository projectRepository;
    private final ProjectEnvironmentRepository environmentRepository;
    private final MonitoredServiceRepository serviceRepository;

    public ProjectResponse createProject(UUID organizationId, CreateProjectRequest request) {
        if (projectRepository.existsByOrganizationIdAndSlug(organizationId, request.slug())) {
            throw new IllegalArgumentException("A project with slug '" + request.slug() + "' already exists in this organization");
        }
        Project project = Project.builder()
                .organizationId(organizationId)
                .name(request.name())
                .slug(request.slug())
                .build();
        project = projectRepository.save(project);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> listProjects(UUID organizationId) {
        return projectRepository.findByOrganizationId(organizationId).stream().map(this::toResponse).toList();
    }

    public EnvironmentResponse createEnvironment(UUID organizationId, UUID projectId, CreateEnvironmentRequest request) {
        Project project = requireProjectInOrg(organizationId, projectId);
        if (environmentRepository.findByProjectIdAndType(project.getId(), request.type()).isPresent()) {
            throw new IllegalArgumentException("Environment '" + request.type() + "' already exists for this project");
        }
        ProjectEnvironment env = ProjectEnvironment.builder()
                .projectId(project.getId())
                .type(request.type())
                .build();
        env = environmentRepository.save(env);
        return new EnvironmentResponse(env.getId(), env.getProjectId(), env.getType(), env.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<EnvironmentResponse> listEnvironments(UUID organizationId, UUID projectId) {
        Project project = requireProjectInOrg(organizationId, projectId);
        return environmentRepository.findByProjectId(project.getId()).stream()
                .map(e -> new EnvironmentResponse(e.getId(), e.getProjectId(), e.getType(), e.getCreatedAt()))
                .toList();
    }

    public ServiceResponse createService(UUID organizationId, UUID projectId, CreateServiceRequest request) {
        Project project = requireProjectInOrg(organizationId, projectId);
        MonitoredService service = MonitoredService.builder()
                .projectId(project.getId())
                .name(request.name())
                .slug(request.slug())
                .description(request.description())
                .build();
        service = serviceRepository.save(service);
        return toResponse(service);
    }

    @Transactional(readOnly = true)
    public List<ServiceResponse> listServices(UUID organizationId, UUID projectId) {
        Project project = requireProjectInOrg(organizationId, projectId);
        return serviceRepository.findByProjectId(project.getId()).stream().map(this::toResponse).toList();
    }

    private Project requireProjectInOrg(UUID organizationId, UUID projectId) {
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new NotFoundException("Project not found: " + projectId));
        if (!project.getOrganizationId().equals(organizationId)) {
            throw new NotFoundException("Project not found: " + projectId);
        }
        return project;
    }

    private ProjectResponse toResponse(Project p) {
        return new ProjectResponse(p.getId(), p.getOrganizationId(), p.getName(), p.getSlug(), p.getCreatedAt());
    }

    private ServiceResponse toResponse(MonitoredService s) {
        return new ServiceResponse(s.getId(), s.getProjectId(), s.getName(), s.getSlug(), s.getDescription(), s.getCreatedAt());
    }
}
