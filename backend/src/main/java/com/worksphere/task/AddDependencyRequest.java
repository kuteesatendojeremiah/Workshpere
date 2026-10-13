package com.worksphere.task;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddDependencyRequest(
        @NotNull(message = "dependsOnTaskId must not be null") UUID dependsOnTaskId) {
}
