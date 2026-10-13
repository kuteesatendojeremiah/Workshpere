package com.worksphere.sprint;

import com.worksphere.common.pagination.PageResponse;
import com.worksphere.task.TaskResponse;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SprintController {

    private final SprintService sprintService;

    public SprintController(SprintService sprintService) {
        this.sprintService = sprintService;
    }

    @PostMapping("/api/v1/projects/{projectId}/sprints")
    @ResponseStatus(HttpStatus.CREATED)
    public SprintResponse create(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody CreateSprintRequest request) {
        return SprintResponse.from(sprintService.createSprint(
                projectId, callerUserId, request.name(), request.goal(), request.startDate(), request.endDate()));
    }

    @GetMapping("/api/v1/projects/{projectId}/sprints")
    public PageResponse<SprintResponse> list(
            @PathVariable UUID projectId, @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(
                sprintService.listSprints(projectId, callerUserId, pageable).map(SprintResponse::from));
    }

    @GetMapping("/api/v1/projects/{projectId}/backlog")
    public PageResponse<TaskResponse> backlog(
            @PathVariable UUID projectId, @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(
                sprintService.listBacklog(projectId, callerUserId, pageable).map(TaskResponse::from));
    }

    @GetMapping("/api/v1/sprints/{sprintId}")
    public SprintResponse get(@PathVariable UUID sprintId, @AuthenticationPrincipal UUID callerUserId) {
        return SprintResponse.from(sprintService.getSprint(sprintId, callerUserId));
    }

    @PostMapping("/api/v1/sprints/{sprintId}/start")
    public SprintResponse start(@PathVariable UUID sprintId, @AuthenticationPrincipal UUID callerUserId) {
        return SprintResponse.from(sprintService.startSprint(sprintId, callerUserId));
    }

    @PostMapping("/api/v1/sprints/{sprintId}/complete")
    public SprintResponse complete(@PathVariable UUID sprintId, @AuthenticationPrincipal UUID callerUserId) {
        return SprintResponse.from(sprintService.completeSprint(sprintId, callerUserId));
    }

    @PostMapping("/api/v1/sprints/{sprintId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public SprintTaskResponse addTask(
            @PathVariable UUID sprintId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody AddSprintTaskRequest request) {
        return SprintTaskResponse.from(
                sprintService.addTaskToSprint(sprintId, callerUserId, request.taskId(), request.storyPoints()));
    }

    @DeleteMapping("/api/v1/sprints/{sprintId}/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeTask(
            @PathVariable UUID sprintId, @PathVariable UUID taskId, @AuthenticationPrincipal UUID callerUserId) {
        sprintService.removeTaskFromSprint(sprintId, callerUserId, taskId);
    }

    @GetMapping("/api/v1/sprints/{sprintId}/tasks")
    public List<SprintTaskResponse> listTasks(
            @PathVariable UUID sprintId, @AuthenticationPrincipal UUID callerUserId) {
        return sprintService.listSprintTasks(sprintId, callerUserId).stream()
                .map(SprintTaskResponse::from)
                .toList();
    }

    @GetMapping("/api/v1/sprints/{sprintId}/velocity")
    public VelocityResult velocity(@PathVariable UUID sprintId, @AuthenticationPrincipal UUID callerUserId) {
        return sprintService.getVelocity(sprintId, callerUserId);
    }

    @GetMapping("/api/v1/sprints/{sprintId}/burndown")
    public List<BurndownPoint> burndown(@PathVariable UUID sprintId, @AuthenticationPrincipal UUID callerUserId) {
        return sprintService.getBurndown(sprintId, callerUserId);
    }
}
