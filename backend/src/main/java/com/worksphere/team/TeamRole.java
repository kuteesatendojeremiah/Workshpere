package com.worksphere.team;

/**
 * A per-team role, distinct from the organization-wide {@code common.security.Role}/permission
 * system. A LEAD can manage their own team's membership without needing the org-wide
 * MANAGE_TEAMS permission; an org owner/project manager can manage any team via that permission.
 */
public enum TeamRole {
    LEAD,
    MEMBER
}
