package com.worksphere.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateLabelRequest(
        @NotBlank(message = "name must not be blank")
                @Size(max = 100, message = "name must be at most 100 characters")
                String name,
        @NotBlank(message = "color must not be blank")
                @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "color must be a hex color like #RRGGBB")
                String color) {
}
