package com.worksphere.task;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AttachLabelRequest(@NotNull(message = "labelId must not be null") UUID labelId) {
}
