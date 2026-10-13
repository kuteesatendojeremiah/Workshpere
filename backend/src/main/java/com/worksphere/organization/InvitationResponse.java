package com.worksphere.organization;

import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(UUID id, String email, String role, Instant expiresAt) {

    public static InvitationResponse from(Invitation invitation) {
        return new InvitationResponse(
                invitation.getId(), invitation.getEmail(), invitation.getRole().getName(), invitation.getExpiresAt());
    }
}
