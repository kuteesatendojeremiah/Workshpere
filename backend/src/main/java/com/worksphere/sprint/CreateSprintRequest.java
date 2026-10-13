package com.worksphere.sprint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CreateSprintRequest(
        @NotBlank(message = "name must not be blank")
                @Size(max = 255, message = "name must be at most 255 characters")
                String name,
        String goal,
        @NotNull(message = "startDate must not be null") LocalDate startDate,
        @NotNull(message = "endDate must not be null") LocalDate endDate) {
}
