package com.worksphere.organization;

/**
 * The subset of seeded {@link com.worksphere.common.security.Role} names that can actually be
 * assigned within an organization - deliberately excludes PLATFORM_ADMIN, which isn't an
 * organization-scoped role. Used only to validate/default the role on an invite request; the
 * real permission data lives in the database-seeded roles/permissions tables.
 */
public enum OrganizationRoleName {
    ORGANIZATION_OWNER,
    PROJECT_MANAGER,
    DEVELOPER,
    EMPLOYEE_MEMBER
}
