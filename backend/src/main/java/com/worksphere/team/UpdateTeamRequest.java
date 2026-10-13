package com.worksphere.team;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateTeamRequest(
        @NotBlank(message = "name must not be blank")
                @Size(max = 255, message = "name must be at most 255 characters")
                String name) {
}
