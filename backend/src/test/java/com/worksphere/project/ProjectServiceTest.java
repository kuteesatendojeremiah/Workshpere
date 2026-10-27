package com.worksphere.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.Role;
import com.worksphere.common.security.TestRoles;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.organization.Organization;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProjectServiceTest {

    @Mock
    private com.worksphere.billing.BillingService billingService;

    @Mock
    private com.worksphere.common.outbox.OutboxService outboxService;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ProjectMemberRepository projectMemberRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private final PermissionChecker permissionChecker = new PermissionChecker();
    private final Role pmRole = TestRoles.role("PROJECT_MANAGER", "MANAGE_PROJECTS", "VIEW_ALL_PROJECTS");
    private final Role memberRole = TestRoles.role("EMPLOYEE_MEMBER");

    private ProjectService projectService;

    @BeforeEach
    void setUp() {
        projectService = new ProjectService(
                projectRepository,
                projectMemberRepository,
                organizationService,
                userService,
                permissionChecker,
                auditService, outboxService, billingService);
    }

    @Test
    void createProjectRequiresManagePermission() {
        UUID organizationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("member@example.com", "hash", "Member");
        OrganizationMember membership = new OrganizationMember(organization, caller, memberRole);
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);

        assertThatThrownBy(() -> projectService.createProject(organizationId, callerId, "Website", null))
                .isInstanceOf(MissingPermissionException.class);
    }

    @Test
    void memberWithoutViewAllCannotSeeProjectTheyAreNotPartOf() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("member@example.com", "hash", "Member");
        OrganizationMember membership = new OrganizationMember(organization, caller, memberRole);
        Project project = new Project(organization, "Website", null);
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);
        when(projectRepository.findByIdAndOrganizationId(projectId, organizationId)).thenReturn(Optional.of(project));
        when(projectMemberRepository.findByProjectIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject(organizationId, projectId, callerId))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void projectManagerCanSeeAnyProjectEvenWithoutMembership() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("pm@example.com", "hash", "PM");
        OrganizationMember membership = new OrganizationMember(organization, caller, pmRole);
        Project project = new Project(organization, "Website", null);
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);
        when(projectRepository.findByIdAndOrganizationId(projectId, organizationId)).thenReturn(Optional.of(project));

        Project result = projectService.getProject(organizationId, projectId, callerId);

        assertThat(result).isEqualTo(project);
    }

    @Test
    void projectFromDifferentOrganizationIsNotFound() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("pm@example.com", "hash", "PM");
        OrganizationMember membership = new OrganizationMember(organization, caller, pmRole);
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);
        when(projectRepository.findByIdAndOrganizationId(projectId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProject(organizationId, projectId, callerId))
                .isInstanceOf(ProjectNotFoundException.class);
    }

    @Test
    void archiveProjectChangesStatus() {
        UUID organizationId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("pm@example.com", "hash", "PM");
        OrganizationMember membership = new OrganizationMember(organization, caller, pmRole);
        Project project = new Project(organization, "Website", null);
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);
        when(projectRepository.findByIdAndOrganizationId(projectId, organizationId)).thenReturn(Optional.of(project));

        Project result = projectService.archiveProject(organizationId, projectId, callerId);

        assertThat(result.getStatus()).isEqualTo(ProjectStatus.ARCHIVED);
    }
}
