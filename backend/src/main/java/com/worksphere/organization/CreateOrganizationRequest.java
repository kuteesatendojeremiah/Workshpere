package com.worksphere.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
        @NotBlank(message = "name must not be blank")
                @Size(max = 255, message = "name must be at most 255 characters")
                String name) {
}
