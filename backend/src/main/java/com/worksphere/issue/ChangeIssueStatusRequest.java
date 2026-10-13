package com.worksphere.issue;

import jakarta.validation.constraints.NotNull;

public record ChangeIssueStatusRequest(@NotNull(message = "status must not be null") IssueStatus status) {
}
