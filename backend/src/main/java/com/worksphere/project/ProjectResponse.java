package com.worksphere.project;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(
        UUID id, UUID organizationId, String name, String description, ProjectStatus status, Instant createdAt) {

    public static ProjectResponse from(Project project) {
        return new ProjectResponse(
                project.getId(),
                project.getOrganization().getId(),
                project.getName(),
                project.getDescription(),
                project.getStatus(),
                project.getCreatedAt());
    }
}
