package com.worksphere.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "token must not be blank") String token,
        @NotBlank(message = "newPassword must not be blank")
                @Size(min = 8, max = 100, message = "newPassword must be between 8 and 100 characters")
                String newPassword) {
}
