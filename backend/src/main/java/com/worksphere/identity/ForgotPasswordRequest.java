package com.worksphere.identity;

import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "email must not be blank") String email) {
}
