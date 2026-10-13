package com.worksphere.project;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddProjectMemberRequest(@NotNull(message = "userId must not be null") UUID userId) {
}
