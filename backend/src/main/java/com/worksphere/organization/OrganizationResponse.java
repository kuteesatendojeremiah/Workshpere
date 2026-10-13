package com.worksphere.organization;

import java.time.Instant;
import java.util.UUID;

public record OrganizationResponse(UUID id, String name, Instant createdAt) {

    public static OrganizationResponse from(Organization organization) {
        return new OrganizationResponse(organization.getId(), organization.getName(), organization.getCreatedAt());
    }
}
