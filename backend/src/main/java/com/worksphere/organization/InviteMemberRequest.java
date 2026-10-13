package com.worksphere.organization;

import jakarta.validation.constraints.NotBlank;

public record InviteMemberRequest(
        @NotBlank(message = "email must not be blank") String email, String role) {
}
