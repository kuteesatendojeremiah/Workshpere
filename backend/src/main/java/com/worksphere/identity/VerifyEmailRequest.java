package com.worksphere.identity;

import jakarta.validation.constraints.NotBlank;

public record VerifyEmailRequest(@NotBlank(message = "token must not be blank") String token) {
}
