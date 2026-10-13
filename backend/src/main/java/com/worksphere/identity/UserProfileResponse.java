package com.worksphere.identity;

import java.time.Instant;
import java.util.UUID;

public record UserProfileResponse(
        UUID id, String email, String displayName, boolean emailVerified, Instant createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.isEmailVerified(),
                user.getCreatedAt());
    }
}
