package com.worksphere.team;

import java.time.Instant;
import java.util.UUID;

public record TeamResponse(UUID id, UUID organizationId, String name, Instant createdAt) {

    public static TeamResponse from(Team team) {
        return new TeamResponse(team.getId(), team.getOrganization().getId(), team.getName(), team.getCreatedAt());
    }
}
