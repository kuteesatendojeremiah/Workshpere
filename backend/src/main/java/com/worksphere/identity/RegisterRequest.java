package com.worksphere.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "email must not be blank")
                @Email(message = "email must be a valid email address")
                String email,
        @NotBlank(message = "password must not be blank")
                @Size(min = 8, max = 100, message = "password must be between 8 and 100 characters")
                String password,
        @NotBlank(message = "displayName must not be blank")
                @Size(max = 255, message = "displayName must be at most 255 characters")
                String displayName) {
}
