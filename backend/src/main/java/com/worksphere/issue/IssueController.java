package com.worksphere.issue;

import com.worksphere.common.pagination.PageResponse;
import jakarta.validation.Valid;
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
public class IssueController {

    private final IssueService issueService;

    public IssueController(IssueService issueService) {
        this.issueService = issueService;
    }

    @PostMapping("/api/v1/projects/{projectId}/issues")
    @ResponseStatus(HttpStatus.CREATED)
    public IssueResponse report(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody ReportIssueRequest request) {
        return IssueResponse.from(issueService.reportIssue(
                projectId,
                callerUserId,
                request.title(),
                request.description(),
                request.severity(),
                request.priority(),
                request.environment()));
    }

    @GetMapping("/api/v1/projects/{projectId}/issues")
    public PageResponse<IssueResponse> list(
            @PathVariable UUID projectId,
            @AuthenticationPrincipal UUID callerUserId,
            @RequestParam(required = false) IssueSeverity severity,
            @RequestParam(required = false) IssuePriority priority,
            @RequestParam(required = false) IssueEnvironment environment,
            @RequestParam(required = false) IssueStatus status,
            Pageable pageable) {
        IssueFilter filter = new IssueFilter(severity, priority, environment, status);
        return PageResponse.from(
                issueService.listIssues(projectId, callerUserId, filter, pageable).map(IssueResponse::from));
    }

    @GetMapping("/api/v1/issues/{issueId}")
    public IssueResponse get(@PathVariable UUID issueId, @AuthenticationPrincipal UUID callerUserId) {
        return IssueResponse.from(issueService.getIssue(issueId, callerUserId));
    }

    @PutMapping("/api/v1/issues/{issueId}")
    public IssueResponse update(
            @PathVariable UUID issueId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody UpdateIssueRequest request) {
        return IssueResponse.from(issueService.updateIssue(
                issueId,
                callerUserId,
                request.title(),
                request.description(),
                request.severity(),
                request.priority(),
                request.environment()));
    }

    @PostMapping("/api/v1/issues/{issueId}/status")
    public IssueResponse changeStatus(
            @PathVariable UUID issueId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody ChangeIssueStatusRequest request) {
        return IssueResponse.from(issueService.changeStatus(issueId, callerUserId, request.status()));
    }

    @PostMapping("/api/v1/issues/{issueId}/assignee")
    public IssueResponse assign(
            @PathVariable UUID issueId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody AssignIssueRequest request) {
        return IssueResponse.from(issueService.assignIssue(issueId, callerUserId, request.assigneeId()));
    }

    @PostMapping("/api/v1/issues/{issueId}/link-task")
    public IssueResponse linkTask(
            @PathVariable UUID issueId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody LinkTaskRequest request) {
        return IssueResponse.from(issueService.linkTask(issueId, callerUserId, request.taskId()));
    }

    @DeleteMapping("/api/v1/issues/{issueId}/link-task")
    public IssueResponse unlinkTask(@PathVariable UUID issueId, @AuthenticationPrincipal UUID callerUserId) {
        return IssueResponse.from(issueService.unlinkTask(issueId, callerUserId));
    }
}
