package com.worksphere.team;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddTeamMemberRequest(@NotNull(message = "userId must not be null") UUID userId, TeamRole teamRole) {

    public TeamRole teamRoleOrDefault() {
        return teamRole != null ? teamRole : TeamRole.MEMBER;
    }
}
