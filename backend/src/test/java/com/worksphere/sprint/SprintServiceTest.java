package com.worksphere.sprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.Role;
import com.worksphere.common.security.TestRoles;
import com.worksphere.organization.Organization;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationService;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectService;
import com.worksphere.task.Task;
import com.worksphere.task.TaskPriority;
import com.worksphere.task.TaskService;
import com.worksphere.task.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SprintServiceTest {

    @Mock
    private com.worksphere.common.outbox.OutboxService outboxService;

    @Mock
    private SprintRepository sprintRepository;

    @Mock
    private SprintTaskRepository sprintTaskRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private TaskService taskService;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private AuditService auditService;

    private final PermissionChecker permissionChecker = new PermissionChecker();
    private final Role pmRole = TestRoles.role("PROJECT_MANAGER", "MANAGE_SPRINTS");
    private final Role memberRole = TestRoles.role("EMPLOYEE_MEMBER");

    private SprintService sprintService;

    @BeforeEach
    void setUp() {
        sprintService = new SprintService(
                sprintRepository,
                sprintTaskRepository,
                projectService,
                taskService,
                organizationService,
                permissionChecker,
                auditService, outboxService);
    }

    private Project projectWith(Organization organization) {
        return new Project(organization, "Website", null);
    }

    @Test
    void createSprintRequiresManageSprintsPermission() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        Project project = projectWith(organization);
        when(projectService.requireProjectAccess(projectId, callerId)).thenReturn(project);
        when(organizationService.requireMembership(organization.getId(), callerId))
                .thenReturn(new OrganizationMember(organization, null, memberRole));

        assertThatThrownBy(() -> sprintService.createSprint(
                        projectId, callerId, "Sprint 1", null, LocalDate.now(), LocalDate.now().plusWeeks(2)))
                .isInstanceOf(MissingPermissionException.class);
    }

    @Test
    void createSprintRejectsEndDateBeforeStartDate() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        Project project = projectWith(organization);
        when(projectService.requireProjectAccess(projectId, callerId)).thenReturn(project);
        when(organizationService.requireMembership(organization.getId(), callerId))
                .thenReturn(new OrganizationMember(organization, null, pmRole));

        assertThatThrownBy(() -> sprintService.createSprint(
                        projectId, callerId, "Sprint 1", null, LocalDate.now(), LocalDate.now().minusDays(1)))
                .isInstanceOf(InvalidSprintStateException.class);
    }

    @Test
    void cannotStartASprintThatIsNotPlanned() {
        UUID sprintId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        Project project = projectWith(organization);
        Sprint sprint = new Sprint(project, "Sprint 1", null, LocalDate.now(), LocalDate.now().plusWeeks(2));
        sprint.start();
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(organizationService.requireMembership(organization.getId(), callerId))
                .thenReturn(new OrganizationMember(organization, null, pmRole));

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, callerId))
                .isInstanceOf(InvalidSprintStateException.class);
    }

    @Test
    void cannotStartASecondActiveSprintInTheSameProject() {
        UUID sprintId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        Project project = projectWith(organization);
        Sprint sprint = new Sprint(project, "Sprint 2", null, LocalDate.now(), LocalDate.now().plusWeeks(2));
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(organizationService.requireMembership(organization.getId(), callerId))
                .thenReturn(new OrganizationMember(organization, null, pmRole));
        when(sprintRepository.existsByProject_IdAndStatus(project.getId(), SprintStatus.ACTIVE)).thenReturn(true);

        assertThatThrownBy(() -> sprintService.startSprint(sprintId, callerId))
                .isInstanceOf(InvalidSprintStateException.class);
    }

    @Test
    void addingTaskFromDifferentProjectIsRejected() {
        UUID sprintId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        Project sprintProject = projectWith(organization);
        Project otherProject = new Project(organization, "Other Project", null);
        ReflectionTestUtils.setField(sprintProject, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(otherProject, "id", UUID.randomUUID());
        Sprint sprint = new Sprint(sprintProject, "Sprint 1", null, LocalDate.now(), LocalDate.now().plusWeeks(2));
        Task taskFromOtherProject = new Task(otherProject, "Other task", null, TaskPriority.MEDIUM, null);
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectService.requireProjectAccess(sprintProject.getId(), callerId)).thenReturn(sprintProject);
        when(organizationService.requireMembership(organization.getId(), callerId))
                .thenReturn(new OrganizationMember(organization, null, pmRole));
        when(taskService.getTask(taskId, callerId)).thenReturn(taskFromOtherProject);

        assertThatThrownBy(() -> sprintService.addTaskToSprint(sprintId, callerId, taskId, 5))
                .isInstanceOf(InvalidSprintTaskException.class);
    }

    @Test
    void velocityCountsOnlyCompletedTasksTowardCompletedPoints() {
        UUID sprintId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        Project project = projectWith(organization);
        Sprint sprint = new Sprint(project, "Sprint 1", null, LocalDate.now(), LocalDate.now().plusWeeks(2));
        Task doneTask = new Task(project, "Done task", null, TaskPriority.MEDIUM, null);
        doneTask.moveTo(TaskStatus.IN_PROGRESS, 0);
        doneTask.moveTo(TaskStatus.IN_REVIEW, 0);
        doneTask.moveTo(TaskStatus.DONE, 0);
        Task pendingTask = new Task(project, "Pending task", null, TaskPriority.MEDIUM, null);
        when(sprintRepository.findById(sprintId)).thenReturn(Optional.of(sprint));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(sprintTaskRepository.findAllBySprintId(sprintId))
                .thenReturn(List.of(new SprintTask(sprint, doneTask, 5), new SprintTask(sprint, pendingTask, 3)));

        VelocityResult result = sprintService.getVelocity(sprintId, callerId);

        assertThat(result.committedPoints()).isEqualTo(8);
        assertThat(result.completedPoints()).isEqualTo(5);
    }
}
