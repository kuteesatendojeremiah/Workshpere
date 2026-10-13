package com.worksphere.project;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateProjectRequest(
        @NotBlank(message = "name must not be blank")
                @Size(max = 255, message = "name must be at most 255 characters")
                String name,
        @Size(max = 4000, message = "description must be at most 4000 characters") String description) {
}
