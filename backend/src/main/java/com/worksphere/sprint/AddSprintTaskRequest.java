package com.worksphere.sprint;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record AddSprintTaskRequest(
        @NotNull(message = "taskId must not be null") UUID taskId,
        @PositiveOrZero(message = "storyPoints must not be negative") int storyPoints) {
}
