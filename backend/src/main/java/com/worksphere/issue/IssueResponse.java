package com.worksphere.issue;

import java.time.Instant;
import java.util.UUID;

public record IssueResponse(
        UUID id,
        UUID projectId,
        String title,
        String description,
        IssueSeverity severity,
        IssuePriority priority,
        IssueEnvironment environment,
        IssueStatus status,
        UUID reporterId,
        UUID assigneeId,
        UUID linkedTaskId,
        Instant createdAt,
        Instant updatedAt) {

    public static IssueResponse from(Issue issue) {
        return new IssueResponse(
                issue.getId(),
                issue.getProject().getId(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getSeverity(),
                issue.getPriority(),
                issue.getEnvironment(),
                issue.getStatus(),
                issue.getReporter().getId(),
                issue.getAssignee() != null ? issue.getAssignee().getId() : null,
                issue.getLinkedTask() != null ? issue.getLinkedTask().getId() : null,
                issue.getCreatedAt(),
                issue.getUpdatedAt());
    }
}
