package com.worksphere.organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizationMemberResponse(
        UUID userId, String email, String displayName, String role, Instant joinedAt) {

    public static OrganizationMemberResponse from(OrganizationMember member) {
        return new OrganizationMemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getRole().getName(),
                member.getCreatedAt());
    }
}
