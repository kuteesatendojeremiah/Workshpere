package com.worksphere.issue;

public record IssueFilter(IssueSeverity severity, IssuePriority priority, IssueEnvironment environment, IssueStatus status) {
}
