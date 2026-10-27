package com.worksphere.task;

import com.worksphere.common.error.StaleVersionException;
import com.worksphere.common.realtime.RealtimePublisher;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.notification.NotificationService;
import com.worksphere.notification.NotificationType;
import com.worksphere.project.Project;
import com.worksphere.project.ProjectNotFoundException;
import com.worksphere.project.ProjectService;
import java.time.LocalDate;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskDependencyRepository taskDependencyRepository;
    private final ProjectService projectService;
    private final UserService userService;
    private final NotificationService notificationService;
    private final RealtimePublisher realtimePublisher;
    private final TaskSummaryCache taskSummaryCache;

    public TaskService(
            TaskRepository taskRepository,
            TaskDependencyRepository taskDependencyRepository,
            ProjectService projectService,
            UserService userService,
            NotificationService notificationService,
            RealtimePublisher realtimePublisher,
            TaskSummaryCache taskSummaryCache) {
        this.taskRepository = taskRepository;
        this.taskDependencyRepository = taskDependencyRepository;
        this.projectService = projectService;
        this.userService = userService;
        this.notificationService = notificationService;
        this.realtimePublisher = realtimePublisher;
        this.taskSummaryCache = taskSummaryCache;
    }

    @Transactional
    public Task createTask(
            UUID projectId, UUID callerUserId, String title, String description, TaskPriority priority, LocalDate dueDate) {
        Project project = projectService.requireProjectAccess(projectId, callerUserId);
        Task task = new Task(project, title, description, priority, dueDate);
        task.setPosition(taskRepository.countByProject_IdAndStatus(projectId, TaskStatus.TODO));
        Task saved = taskRepository.save(task);
        taskSummaryCache.evictAfterCommit(projectId);
        realtimePublisher.boardChanged(projectId, "TASK_CREATED", saved.getId());
        return saved;
    }

    @Transactional(readOnly = true)
    public Task getTask(UUID taskId, UUID callerUserId) {
        return requireTaskAccess(taskId, callerUserId);
    }

    /**
     * Per-column task counts, served from Redis when possible. The access check comes first, so the
     * cache can never hand data to someone who may not see the project.
     */
    @Transactional(readOnly = true)
    public TaskSummaryResponse getSummary(UUID projectId, UUID callerUserId) {
        projectService.requireProjectAccess(projectId, callerUserId);

        return taskSummaryCache.get(projectId).orElseGet(() -> {
            Map<TaskStatus, Integer> counts = new EnumMap<>(TaskStatus.class);
            int total = 0;
            for (TaskStatus status : TaskStatus.values()) {
                int count = taskRepository.countByProject_IdAndStatus(projectId, status);
                counts.put(status, count);
                total += count;
            }
            TaskSummaryResponse summary = new TaskSummaryResponse(projectId, counts, total);
            taskSummaryCache.put(summary);
            return summary;
        });
    }

    @Transactional(readOnly = true)
    public Page<Task> listTasks(UUID projectId, UUID callerUserId, TaskFilter filter, Pageable pageable) {
        projectService.requireProjectAccess(projectId, callerUserId);

        var spec = TaskSpecifications.inProject(projectId)
                .and(TaskSpecifications.hasStatus(filter.status()))
                .and(TaskSpecifications.hasPriority(filter.priority()))
                .and(TaskSpecifications.hasAssignee(filter.assigneeId()));
        return taskRepository.findAll(spec, pageable);
    }

    /**
     * Sprint-agnostic by design: the task module has no business knowing what a sprint is.
     * {@code excludedTaskIds} lets a caller like {@code SprintService} compute "backlog" (tasks
     * not currently in a planned/active sprint) without this module depending on that one.
     */
    @Transactional(readOnly = true)
    public Page<Task> listTasksExcluding(
            UUID projectId, UUID callerUserId, Collection<UUID> excludedTaskIds, Pageable pageable) {
        projectService.requireProjectAccess(projectId, callerUserId);

        var spec = TaskSpecifications.inProject(projectId).and(TaskSpecifications.idNotIn(excludedTaskIds));
        return taskRepository.findAll(spec, pageable);
    }

    @Transactional
    public Task updateTask(
            UUID taskId,
            UUID callerUserId,
            String title,
            String description,
            TaskPriority priority,
            LocalDate dueDate,
            long expectedVersion) {
        Task task = requireTaskAccess(taskId, callerUserId);
        requireCurrentVersion(task, expectedVersion);

        task.updateDetails(title, description, priority, dueDate);
        realtimePublisher.boardChanged(task.getProject().getId(), "TASK_UPDATED", task.getId());
        return task;
    }

    /**
     * Moves a task to {@code newStatus} (the Kanban column) at {@code targetPosition} within it
     * ({@code null} means "append to the end"). The server always recomputes every affected
     * task's position itself from the requested insertion point - per section 23, client-supplied
     * ordering is never trusted as the final order, only as a hint of where to insert.
     */
    @Transactional
    public Task changeStatus(
            UUID taskId, UUID callerUserId, TaskStatus newStatus, Integer targetPosition, long expectedVersion) {
        Task task = requireTaskAccess(taskId, callerUserId);
        requireCurrentVersion(task, expectedVersion);

        TaskStatus currentStatus = task.getStatus();
        UUID projectId = task.getProject().getId();

        if (newStatus != currentStatus && !currentStatus.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(currentStatus, newStatus);
        }

        if (newStatus != currentStatus) {
            List<Task> sourceColumn =
                    taskRepository.findAllByProject_IdAndStatusOrderByPositionAsc(projectId, currentStatus);
            sourceColumn.remove(task);
            reassignPositions(sourceColumn);
        }

        List<Task> destinationColumn =
                taskRepository.findAllByProject_IdAndStatusOrderByPositionAsc(projectId, newStatus);
        destinationColumn.remove(task);

        int insertAt = targetPosition == null
                ? destinationColumn.size()
                : Math.max(0, Math.min(targetPosition, destinationColumn.size()));
        destinationColumn.add(insertAt, task);

        for (int i = 0; i < destinationColumn.size(); i++) {
            Task columnTask = destinationColumn.get(i);
            if (columnTask == task) {
                task.moveTo(newStatus, i);
            } else {
                columnTask.setPosition(i);
            }
        }

        if (newStatus != currentStatus && task.getAssignee() != null
                && !task.getAssignee().getId().equals(callerUserId)) {
            notificationService.notify(
                    task.getAssignee().getId(),
                    NotificationType.TASK_STATUS_CHANGED,
                    "Task '" + task.getTitle() + "' status changed to " + newStatus,
                    "TASK",
                    task.getId());
        }

        if (newStatus != currentStatus) {
            taskSummaryCache.evictAfterCommit(projectId);
        }
        realtimePublisher.boardChanged(projectId, "TASK_MOVED", task.getId());
        return task;
    }

    private void reassignPositions(List<Task> columnTasks) {
        for (int i = 0; i < columnTasks.size(); i++) {
            columnTasks.get(i).setPosition(i);
        }
    }

    @Transactional
    public Task assignTask(UUID taskId, UUID callerUserId, UUID assigneeId, long expectedVersion) {
        Task task = requireTaskAccess(taskId, callerUserId);
        requireCurrentVersion(task, expectedVersion);

        if (assigneeId == null) {
            task.assignTo(null);
            realtimePublisher.boardChanged(task.getProject().getId(), "TASK_UPDATED", task.getId());
            return task;
        }

        if (!projectService.isProjectMember(task.getProject().getId(), assigneeId)) {
            throw new UserNotInProjectException(assigneeId);
        }

        User assignee = userService.getProfile(assigneeId);
        task.assignTo(assignee);

        if (!assigneeId.equals(callerUserId)) {
            notificationService.notify(
                    assigneeId,
                    NotificationType.TASK_ASSIGNED,
                    "You were assigned to task '" + task.getTitle() + "'",
                    "TASK",
                    task.getId());
        }
        realtimePublisher.boardChanged(task.getProject().getId(), "TASK_UPDATED", task.getId());
        return task;
    }

    @Transactional
    public void deleteTask(UUID taskId, UUID callerUserId) {
        Task task = requireTaskAccess(taskId, callerUserId);
        taskRepository.delete(task);
        taskSummaryCache.evictAfterCommit(task.getProject().getId());
        realtimePublisher.boardChanged(task.getProject().getId(), "TASK_DELETED", task.getId());
    }

    @Transactional
    public TaskDependency addDependency(UUID taskId, UUID callerUserId, UUID dependsOnTaskId) {
        Task task = requireTaskAccess(taskId, callerUserId);

        if (taskId.equals(dependsOnTaskId)) {
            throw new InvalidTaskDependencyException("A task cannot depend on itself");
        }

        Task dependsOnTask = taskRepository
                .findByIdAndProjectId(dependsOnTaskId, task.getProject().getId())
                .orElseThrow(() ->
                        new InvalidTaskDependencyException("The depended-on task must be in the same project"));

        return taskDependencyRepository
                .findByTaskIdAndDependsOnTaskId(taskId, dependsOnTaskId)
                .orElseGet(() -> taskDependencyRepository.save(new TaskDependency(task, dependsOnTask)));
    }

    @Transactional
    public void removeDependency(UUID taskId, UUID callerUserId, UUID dependsOnTaskId) {
        requireTaskAccess(taskId, callerUserId);
        taskDependencyRepository.deleteByTaskIdAndDependsOnTaskId(taskId, dependsOnTaskId);
    }

    @Transactional(readOnly = true)
    public List<TaskDependency> listDependencies(UUID taskId, UUID callerUserId) {
        requireTaskAccess(taskId, callerUserId);
        return taskDependencyRepository.findAllByTaskId(taskId);
    }

    /** Package-visible so comment/label services can reuse this exact check. */
    Task requireTaskAccess(UUID taskId, UUID callerUserId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new TaskNotFoundException(taskId));

        try {
            projectService.requireProjectAccess(task.getProject().getId(), callerUserId);
        } catch (ProjectNotFoundException e) {
            // The URL only ever mentioned a task id - don't leak that a project exists either.
            throw new TaskNotFoundException(taskId);
        }

        return task;
    }

    private void requireCurrentVersion(Task task, long expectedVersion) {
        if (task.getVersion() != expectedVersion) {
            throw new StaleVersionException();
        }
    }
}
