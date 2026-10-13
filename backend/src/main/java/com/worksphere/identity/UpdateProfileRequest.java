package com.worksphere.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "displayName must not be blank")
                @Size(max = 255, message = "displayName must be at most 255 characters")
                String displayName) {
}
