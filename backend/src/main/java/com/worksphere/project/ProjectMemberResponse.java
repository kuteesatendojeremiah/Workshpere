package com.worksphere.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectMemberResponse(UUID userId, String email, String displayName, Instant joinedAt) {

    public static ProjectMemberResponse from(ProjectMember member) {
        return new ProjectMemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getCreatedAt());
    }
}
