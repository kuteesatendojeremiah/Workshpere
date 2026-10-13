package com.worksphere.project;

import com.worksphere.common.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping("/api/v1/organizations/{organizationId}/projects")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectResponse create(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody CreateProjectRequest request) {
        return ProjectResponse.from(
                projectService.createProject(organizationId, callerUserId, request.name(), request.description()));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/projects/{projectId}")
    public ProjectResponse get(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId) {
        return ProjectResponse.from(projectService.getProject(organizationId, projectId, callerUserId));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/projects")
    public PageResponse<ProjectResponse> list(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(
                projectService.listProjects(organizationId, callerUserId, pageable).map(ProjectResponse::from));
    }

    @PutMapping("/api/v1/organizations/{organizationId}/projects/{projectId}")
    public ProjectResponse update(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody UpdateProjectRequest request) {
        return ProjectResponse.from(projectService.updateProject(
                organizationId, projectId, callerUserId, request.name(), request.description()));
    }

    @PostMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/archive")
    public ProjectResponse archive(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId) {
        return ProjectResponse.from(projectService.archiveProject(organizationId, projectId, callerUserId));
    }

    @PostMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectMemberResponse addMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody AddProjectMemberRequest request) {
        return ProjectMemberResponse.from(
                projectService.addMember(organizationId, projectId, callerUserId, request.userId()));
    }

    @DeleteMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UUID callerUserId) {
        projectService.removeMember(organizationId, projectId, callerUserId, userId);
    }

    @GetMapping("/api/v1/organizations/{organizationId}/projects/{projectId}/members")
    public PageResponse<ProjectMemberResponse> listMembers(
            @PathVariable UUID organizationId,
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            Pageable pageable) {
        return PageResponse.from(projectService
                .listMembers(organizationId, projectId, callerUserId, pageable)
                .map(ProjectMemberResponse::from));
    }
}
