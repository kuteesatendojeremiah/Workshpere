package com.worksphere.issue;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReportIssueRequest(
        @NotBlank(message = "title must not be blank")
                @Size(max = 255, message = "title must be at most 255 characters")
                String title,
        @Size(max = 4000, message = "description must be at most 4000 characters") String description,
        IssueSeverity severity,
        IssuePriority priority,
        IssueEnvironment environment) {
}
