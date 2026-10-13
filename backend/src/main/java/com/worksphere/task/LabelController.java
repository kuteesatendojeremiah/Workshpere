package com.worksphere.task;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
public class LabelController {

    private final LabelService labelService;

    public LabelController(LabelService labelService) {
        this.labelService = labelService;
    }

    @PostMapping("/api/v1/projects/{projectId}/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse create(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody CreateLabelRequest request) {
        return LabelResponse.from(
                labelService.createLabel(projectId, callerUserId, request.name(), request.color()));
    }

    @GetMapping("/api/v1/projects/{projectId}/labels")
    public List<LabelResponse> list(
            @PathVariable UUID projectId, @AuthenticationPrincipal UUID callerUserId) {
        return labelService.listLabels(projectId, callerUserId).stream().map(LabelResponse::from).toList();
    }

    @DeleteMapping("/api/v1/projects/{projectId}/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID projectId,
            @PathVariable UUID labelId,
            @AuthenticationPrincipal UUID callerUserId) {
        labelService.deleteLabel(projectId, labelId, callerUserId);
    }

    @PostMapping("/api/v1/tasks/{taskId}/labels")
    @ResponseStatus(HttpStatus.CREATED)
    public LabelResponse attach(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody AttachLabelRequest request) {
        return LabelResponse.from(labelService.attachLabel(taskId, callerUserId, request.labelId()).getLabel());
    }

    @DeleteMapping("/api/v1/tasks/{taskId}/labels/{labelId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void detach(
            @PathVariable UUID taskId,
            @PathVariable UUID labelId,
            @AuthenticationPrincipal UUID callerUserId) {
        labelService.detachLabel(taskId, callerUserId, labelId);
    }

    @GetMapping("/api/v1/tasks/{taskId}/labels")
    public List<LabelResponse> listOnTask(
            @PathVariable UUID taskId, @AuthenticationPrincipal UUID callerUserId) {
        return labelService.listTaskLabels(taskId, callerUserId).stream()
                .map(taskLabel -> LabelResponse.from(taskLabel.getLabel()))
                .toList();
    }
}
