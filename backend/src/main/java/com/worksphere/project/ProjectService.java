package com.worksphere.project;

import com.worksphere.common.outbox.DomainEventType;
import com.worksphere.common.outbox.OutboxService;
import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.UserNotInOrganizationException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationNotFoundException;
import com.worksphere.organization.OrganizationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectService {

    private static final String MANAGE_PROJECTS = "MANAGE_PROJECTS";
    private static final String VIEW_ALL_PROJECTS = "VIEW_ALL_PROJECTS";

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final PermissionChecker permissionChecker;
    private final OutboxService outboxService;
    private final AuditService auditService;

    public ProjectService(
            ProjectRepository projectRepository,
            ProjectMemberRepository projectMemberRepository,
            OrganizationService organizationService,
            UserService userService,
            PermissionChecker permissionChecker,
            AuditService auditService,
            OutboxService outboxService) {
        this.projectRepository = projectRepository;
        this.projectMemberRepository = projectMemberRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.permissionChecker = permissionChecker;
        this.auditService = auditService;
        this.outboxService = outboxService;
    }

    @Transactional
    public Project createProject(UUID organizationId, UUID callerUserId, String name, String description) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        permissionChecker.requirePermission(callerMembership.getRole(), MANAGE_PROJECTS);

        Project project = projectRepository.save(
                new Project(callerMembership.getOrganization(), name, description));
        projectMemberRepository.save(new ProjectMember(project, callerMembership.getUser()));

        auditService.record(
                organizationId, callerUserId, AuditAction.PROJECT_CREATED, AuditResourceType.PROJECT, project.getId(), null);
        outboxService.record(
                DomainEventType.PROJECT_CREATED,
                "PROJECT",
                project.getId(),
                organizationId,
                java.util.Map.of("createdBy", String.valueOf(callerUserId)));
        return project;
    }

    @Transactional(readOnly = true)
    public Project getProject(UUID organizationId, UUID projectId, UUID callerUserId) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        Project project = requireProjectInOrganization(organizationId, projectId);
        requireProjectVisibility(project, callerUserId, callerMembership);
        return project;
    }

    /**
     * The cross-module reusable check for "can this user touch this project", for modules
     * (task, sprint, issue) whose own routes are addressed by project/task id alone, without an
     * organization id in the URL (per spec section 12.1's flatter convention for those
     * resources) - the organization is derived from the project itself, not supplied by the
     * caller. Mirrors {@link OrganizationService#requireMembership} one level down.
     */
    @Transactional(readOnly = true)
    public Project requireProjectAccess(UUID projectId, UUID callerUserId) {
        Project project = projectRepository.findById(projectId).orElseThrow(() -> new ProjectNotFoundException(projectId));

        OrganizationMember callerMembership;
        try {
            callerMembership = organizationService.requireMembership(
                    project.getOrganization().getId(), callerUserId);
        } catch (OrganizationNotFoundException e) {
            // The caller isn't even in this project's organization - report it as the project
            // not being found, since the URL never mentioned an organization at all.
            throw new ProjectNotFoundException(projectId);
        }

        requireProjectVisibility(project, callerUserId, callerMembership);
        return project;
    }

    @Transactional(readOnly = true)
    public Page<Project> listProjects(UUID organizationId, UUID callerUserId, Pageable pageable) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);

        if (permissionChecker.hasPermission(callerMembership.getRole(), VIEW_ALL_PROJECTS)) {
            return projectRepository.findAllByOrganizationId(organizationId, pageable);
        }

        return projectMemberRepository
                .findAllByProject_Organization_IdAndUser_Id(organizationId, callerUserId, pageable)
                .map(ProjectMember::getProject);
    }

    @Transactional
    public Project updateProject(
            UUID organizationId, UUID projectId, UUID callerUserId, String name, String description) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        Project project = requireProjectInOrganization(organizationId, projectId);
        permissionChecker.requirePermission(callerMembership.getRole(), MANAGE_PROJECTS);

        project.update(name, description);
        return project;
    }

    @Transactional
    public Project archiveProject(UUID organizationId, UUID projectId, UUID callerUserId) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        Project project = requireProjectInOrganization(organizationId, projectId);
        permissionChecker.requirePermission(callerMembership.getRole(), MANAGE_PROJECTS);

        project.archive();
        auditService.record(
                organizationId, callerUserId, AuditAction.PROJECT_ARCHIVED, AuditResourceType.PROJECT, projectId, null);
        return project;
    }

    @Transactional
    public ProjectMember addMember(UUID organizationId, UUID projectId, UUID callerUserId, UUID targetUserId) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        Project project = requireProjectInOrganization(organizationId, projectId);
        permissionChecker.requirePermission(callerMembership.getRole(), MANAGE_PROJECTS);

        try {
            organizationService.requireMembership(organizationId, targetUserId);
        } catch (OrganizationNotFoundException e) {
            throw new UserNotInOrganizationException(targetUserId);
        }

        ProjectMember member = projectMemberRepository
                .findByProjectIdAndUserId(projectId, targetUserId)
                .orElseGet(() -> {
                    User targetUser = userService.getProfile(targetUserId);
                    return projectMemberRepository.save(new ProjectMember(project, targetUser));
                });

        auditService.record(
                organizationId, callerUserId, AuditAction.PROJECT_MEMBER_ADDED, AuditResourceType.PROJECT, projectId, null);
        return member;
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID projectId, UUID callerUserId, UUID targetUserId) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        requireProjectInOrganization(organizationId, projectId);
        permissionChecker.requirePermission(callerMembership.getRole(), MANAGE_PROJECTS);

        projectMemberRepository.deleteByProjectIdAndUserId(projectId, targetUserId);
        auditService.record(
                organizationId, callerUserId, AuditAction.PROJECT_MEMBER_REMOVED, AuditResourceType.PROJECT, projectId, null);
    }

    @Transactional(readOnly = true)
    public Page<ProjectMember> listMembers(UUID organizationId, UUID projectId, UUID callerUserId, Pageable pageable) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        Project project = requireProjectInOrganization(organizationId, projectId);
        requireProjectVisibility(project, callerUserId, callerMembership);

        return projectMemberRepository.findAllByProjectId(projectId, pageable);
    }

    @Transactional(readOnly = true)
    public boolean isProjectMember(UUID projectId, UUID userId) {
        return projectMemberRepository.findByProjectIdAndUserId(projectId, userId).isPresent();
    }

    /** Package-visible so other modules (task, sprint, issue) can reuse this exact check. */
    Project requireProjectInOrganization(UUID organizationId, UUID projectId) {
        return projectRepository
                .findByIdAndOrganizationId(projectId, organizationId)
                .orElseThrow(() -> new ProjectNotFoundException(projectId));
    }

    private void requireProjectVisibility(Project project, UUID callerUserId, OrganizationMember callerMembership) {
        if (permissionChecker.hasPermission(callerMembership.getRole(), VIEW_ALL_PROJECTS)) {
            return;
        }

        boolean isProjectMember = projectMemberRepository
                .findByProjectIdAndUserId(project.getId(), callerUserId)
                .isPresent();
        if (!isProjectMember) {
            throw new ProjectNotFoundException(project.getId());
        }
    }
}
