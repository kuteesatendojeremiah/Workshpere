package com.worksphere.issue;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record LinkTaskRequest(@NotNull(message = "taskId must not be null") UUID taskId) {
}
