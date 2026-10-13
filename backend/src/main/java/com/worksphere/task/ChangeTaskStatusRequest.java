package com.worksphere.task;

import jakarta.validation.constraints.NotNull;

public record ChangeTaskStatusRequest(
        @NotNull(message = "status must not be null") TaskStatus status, Integer targetPosition, long version) {
}
