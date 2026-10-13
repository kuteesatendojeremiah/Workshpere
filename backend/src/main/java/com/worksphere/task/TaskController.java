package com.worksphere.task;

import com.worksphere.common.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @PostMapping("/api/v1/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse create(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody CreateTaskRequest request) {
        return TaskResponse.from(taskService.createTask(
                projectId, callerUserId, request.title(), request.description(), request.priority(), request.dueDate()));
    }

    @GetMapping("/api/v1/projects/{projectId}/tasks")
    public PageResponse<TaskResponse> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) TaskPriority priority,
            @RequestParam(required = false) UUID assigneeId,
            Pageable pageable) {
        TaskFilter filter = new TaskFilter(status, priority, assigneeId);
        return PageResponse.from(
                taskService.listTasks(projectId, callerUserId, filter, pageable).map(TaskResponse::from));
    }

    @GetMapping("/api/v1/tasks/{taskId}")
    public TaskResponse get(@PathVariable UUID taskId, @AuthenticationPrincipal UUID callerUserId) {
        return TaskResponse.from(taskService.getTask(taskId, callerUserId));
    }

    @PutMapping("/api/v1/tasks/{taskId}")
    public TaskResponse update(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody UpdateTaskRequest request) {
        return TaskResponse.from(taskService.updateTask(
                taskId,
                callerUserId,
                request.title(),
                request.description(),
                request.priority(),
                request.dueDate(),
                request.version()));
    }

    @PostMapping("/api/v1/tasks/{taskId}/status")
    public TaskResponse changeStatus(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody ChangeTaskStatusRequest request) {
        return TaskResponse.from(taskService.changeStatus(
                taskId, callerUserId, request.status(), request.targetPosition(), request.version()));
    }

    @PostMapping("/api/v1/tasks/{taskId}/assignee")
    public TaskResponse assign(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody AssignTaskRequest request) {
        return TaskResponse.from(
                taskService.assignTask(taskId, callerUserId, request.assigneeId(), request.version()));
    }

    @DeleteMapping("/api/v1/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID taskId, @AuthenticationPrincipal UUID callerUserId) {
        taskService.deleteTask(taskId, callerUserId);
    }

    @PostMapping("/api/v1/tasks/{taskId}/dependencies")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDependencyResponse addDependency(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody AddDependencyRequest request) {
        return TaskDependencyResponse.from(
                taskService.addDependency(taskId, callerUserId, request.dependsOnTaskId()));
    }

    @DeleteMapping("/api/v1/tasks/{taskId}/dependencies/{dependsOnTaskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeDependency(
            @PathVariable UUID taskId,
            @PathVariable UUID dependsOnTaskId,
            @AuthenticationPrincipal UUID callerUserId) {
        taskService.removeDependency(taskId, callerUserId, dependsOnTaskId);
    }

    @GetMapping("/api/v1/tasks/{taskId}/dependencies")
    public List<TaskDependencyResponse> listDependencies(
            @PathVariable UUID taskId, @AuthenticationPrincipal UUID callerUserId) {
        return taskService.listDependencies(taskId, callerUserId).stream()
                .map(TaskDependencyResponse::from)
                .toList();
    }
}
