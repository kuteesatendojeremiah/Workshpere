package com.worksphere.task;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worksphere.common.error.StaleVersionException;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.notification.NotificationService;
import com.worksphere.organization.Organization;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectNotFoundException;
import com.worksphere.project.ProjectService;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.worksphere.common.outbox.OutboxService outboxService;

    @Mock
    private TaskRepository taskRepository;

    @Mock
    private TaskDependencyRepository taskDependencyRepository;

    @Mock
    private ProjectService projectService;

    @Mock
    private UserService userService;

    @Mock
    private NotificationService notificationService;

    @Mock
    private com.worksphere.common.realtime.RealtimePublisher realtimePublisher;

    @Mock
    private TaskSummaryCache taskSummaryCache;

    private TaskService taskService;

    @BeforeEach
    void setUp() {
        taskService = new TaskService(
                taskRepository, taskDependencyRepository, projectService, userService, notificationService, realtimePublisher, taskSummaryCache, outboxService, eventPublisher);
    }

    @Test
    void createTaskDelegatesToProjectAccessCheck() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        when(projectService.requireProjectAccess(projectId, callerId)).thenReturn(project);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task task = taskService.createTask(projectId, callerId, "Fix bug", null, TaskPriority.HIGH, null);

        assertThat(task.getTitle()).isEqualTo("Fix bug");
        assertThat(task.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void getTaskTranslatesProjectNotFoundIntoTaskNotFound() {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectService.requireProjectAccess(project.getId(), callerId))
                .thenThrow(new ProjectNotFoundException(project.getId()));

        assertThatThrownBy(() -> taskService.getTask(taskId, callerId)).isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void getTaskThrowsTaskNotFoundWhenMissing() {
        UUID taskId = UUID.randomUUID();
        when(taskRepository.findById(taskId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.getTask(taskId, UUID.randomUUID()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void updateTaskRejectsStaleVersion() {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);

        assertThatThrownBy(() ->
                        taskService.updateTask(taskId, callerId, "New title", null, TaskPriority.LOW, null, 99L))
                .isInstanceOf(StaleVersionException.class);
    }

    @Test
    void assignTaskRejectsUserNotInProject() {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(projectService.isProjectMember(project.getId(), assigneeId)).thenReturn(false);

        assertThatThrownBy(() -> taskService.assignTask(taskId, callerId, assigneeId, 0L))
                .isInstanceOf(UserNotInProjectException.class);
    }

    @Test
    void assignTaskSucceedsForProjectMember() {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID assigneeId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        User assignee = new User("dev@example.com", "hash", "Dev");
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(projectService.isProjectMember(project.getId(), assigneeId)).thenReturn(true);
        when(userService.getProfile(assigneeId)).thenReturn(assignee);

        Task result = taskService.assignTask(taskId, callerId, assigneeId, 0L);

        assertThat(result.getAssignee()).isEqualTo(assignee);
    }

    @Test
    void addDependencyRejectsSelfDependency() {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);

        assertThatThrownBy(() -> taskService.addDependency(taskId, callerId, taskId))
                .isInstanceOf(InvalidTaskDependencyException.class);
    }

    @Test
    void addDependencyRejectsTaskFromDifferentProject() {
        UUID taskId = UUID.randomUUID();
        UUID otherTaskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(taskRepository.findByIdAndProjectId(otherTaskId, project.getId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> taskService.addDependency(taskId, callerId, otherTaskId))
                .isInstanceOf(InvalidTaskDependencyException.class);
    }

    @Test
    void createTaskAppendsToEndOfTodoColumn() {
        UUID projectId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        when(projectService.requireProjectAccess(projectId, callerId)).thenReturn(project);
        when(taskRepository.countByProject_IdAndStatus(projectId, TaskStatus.TODO)).thenReturn(2);
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        Task task = taskService.createTask(projectId, callerId, "Third task", null, null, null);

        assertThat(task.getPosition()).isEqualTo(2);
    }

    @Test
    void changeStatusRejectsForwardSkip() {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);

        assertThatThrownBy(() -> taskService.changeStatus(taskId, callerId, TaskStatus.DONE, null, 0L))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    void changeStatusMovesToEndOfDestinationColumnByDefault() {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task task = new Task(project, "Fix bug", null, TaskPriority.MEDIUM, null);
        Task alreadyInProgress = new Task(project, "Other task", null, TaskPriority.MEDIUM, null);
        alreadyInProgress.moveTo(TaskStatus.IN_PROGRESS, 0);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(task));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(taskRepository.findAllByProject_IdAndStatusOrderByPositionAsc(project.getId(), TaskStatus.TODO))
                .thenReturn(new ArrayList<>(List.of(task)));
        when(taskRepository.findAllByProject_IdAndStatusOrderByPositionAsc(project.getId(), TaskStatus.IN_PROGRESS))
                .thenReturn(new ArrayList<>(List.of(alreadyInProgress)));

        Task result = taskService.changeStatus(taskId, callerId, TaskStatus.IN_PROGRESS, null, 0L);

        assertThat(result.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(result.getPosition()).isEqualTo(1);
    }

    @Test
    void changeStatusReordersWithinSameColumn() {
        UUID taskId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Project project = new Project(new Organization("Acme Inc"), "Website", null);
        Task first = new Task(project, "First", null, TaskPriority.MEDIUM, null);
        Task second = new Task(project, "Second", null, TaskPriority.MEDIUM, null);
        first.setPosition(0);
        second.setPosition(1);
        when(taskRepository.findById(taskId)).thenReturn(Optional.of(second));
        when(projectService.requireProjectAccess(project.getId(), callerId)).thenReturn(project);
        when(taskRepository.findAllByProject_IdAndStatusOrderByPositionAsc(project.getId(), TaskStatus.TODO))
                .thenReturn(new ArrayList<>(List.of(first, second)));

        Task result = taskService.changeStatus(taskId, callerId, TaskStatus.TODO, 0, 0L);

        assertThat(result.getPosition()).isEqualTo(0);
        assertThat(first.getPosition()).isEqualTo(1);
    }
}
