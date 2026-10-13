package com.worksphere.issue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.notification.NotificationService;
import com.worksphere.organization.Organization;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectNotFoundException;
import com.worksphere.project.ProjectService;
import com.worksphere.task.Task;
import com.worksphere.task.TaskPriority;
import com.worksphere.task.TaskService;
import com.worksphere.task.UserNotInProjectException;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class IssueServiceTest {

    @Mock
    private IssueRepository issueRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private TaskService taskService;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    private IssueService issueService;

    @BeforeEach
    void setUp() {
        issueService = new IssueService(issueRepository, projectService, taskService, userService, notificationService);
    }

    @Test
    void reportIssueSetsReporterAndDefaults() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        User reporter = new User("reporter@example.com", "hash", "Reporter");
        when(projectService.requireProjectAccess(projectId, reporterId)).thenReturn(project);
        when(userService.getProfile(reporterId)).thenReturn(reporter);
        when(issueRepository.save(any(Issue.class))).thenAnswer(inv -> inv.getArgument(0));

        Issue issue = issueService.reportIssue(projectId, reporterId, "Login broken", null, null, null, null);

        assertThat(issue.getReporter()).isEqualTo(reporter);
        assertThat(issue.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(issue.getStatus()).isEqualTo(IssueStatus.OPEN);
    }

    @Test
    void getIssueTranslatesProjectNotFoundIntoIssueNotFound() {
        UUID issueId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        User reporter = new User("reporter@example.com", "hash", "Reporter");
        Issue issue = new Issue(project, "Bug", null, null, null, null, reporter);
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(projectService.requireProjectAccess(project.getId(), callerId))
                .thenThrow(new ProjectNotFoundException(project.getId()));

        assertThatThrownBy(() -> issueService.getIssue(issueId, callerId)).isInstanceOf(IssueNotFoundException.class);
    }

    @Test
    void assignRejectsUserNotInProject() {
        UUID issueId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        User reporter = new User("reporter@example.com", "hash", "Reporter");
        Issue issue = new Issue(project, "Bug", null, null, null, null, reporter);
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(projectService.isProjectMember(project.getId(), assigneeId)).thenReturn(false);

        assertThatThrownBy(() -> issueService.assignIssue(issueId, callerId, assigneeId))
                .isInstanceOf(UserNotInProjectException.class);
    }

    @Test
    void linkTaskRejectsTaskFromDifferentProject() {
        UUID issueId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        Project issueProject = new Project(organization, "Website", null);
        Project otherProject = new Project(organization, "Other", null);
        ReflectionTestUtils.setField(issueProject, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(otherProject, "id", UUID.randomUUID());
        User reporter = new User("reporter@example.com", "hash", "Reporter");
        Issue issue = new Issue(issueProject, "Bug", null, null, null, null, reporter);
        Task taskFromOtherProject = new Task(otherProject, "Other task", null, TaskPriority.MEDIUM, null);
        when(issueRepository.findById(issueId)).thenReturn(Optional.of(issue));
        when(projectService.requireProjectAccess(issueProject.getId(), callerId)).thenReturn(issueProject);
        when(taskService.getTask(taskId, callerId)).thenReturn(taskFromOtherProject);

        assertThatThrownBy(() -> issueService.linkTask(issueId, callerId, taskId))
                .isInstanceOf(InvalidLinkedTaskException.class);
    }
}
