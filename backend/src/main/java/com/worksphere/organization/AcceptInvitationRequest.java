package com.worksphere.organization;

import jakarta.validation.constraints.NotBlank;

public record AcceptInvitationRequest(@NotBlank(message = "token must not be blank") String token) {
}
