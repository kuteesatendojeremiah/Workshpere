package com.worksphere.team;

import java.time.Instant;
import java.util.UUID;

public record TeamMemberResponse(
        UUID userId, String email, String displayName, TeamRole teamRole, Instant joinedAt) {

    public static TeamMemberResponse from(TeamMember member) {
        return new TeamMemberResponse(
                member.getUser().getId(),
                member.getUser().getEmail(),
                member.getUser().getDisplayName(),
                member.getTeamRole(),
                member.getCreatedAt());
    }
}
