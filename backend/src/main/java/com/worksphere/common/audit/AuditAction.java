package com.worksphere.common.audit;

/** Action name constants, so call sites can't typo an action string differently each time. */
public final class AuditAction {

    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
    public static final String ORGANIZATION_CREATED = "ORGANIZATION_CREATED";
    public static final String MEMBER_INVITED = "MEMBER_INVITED";
    public static final String INVITATION_ACCEPTED = "INVITATION_ACCEPTED";
    public static final String TEAM_CREATED = "TEAM_CREATED";
    public static final String TEAM_DELETED = "TEAM_DELETED";
    public static final String TEAM_MEMBER_ADDED = "TEAM_MEMBER_ADDED";
    public static final String TEAM_MEMBER_REMOVED = "TEAM_MEMBER_REMOVED";
    public static final String PROJECT_CREATED = "PROJECT_CREATED";
    public static final String PROJECT_ARCHIVED = "PROJECT_ARCHIVED";
    public static final String PROJECT_MEMBER_ADDED = "PROJECT_MEMBER_ADDED";
    public static final String PROJECT_MEMBER_REMOVED = "PROJECT_MEMBER_REMOVED";
    public static final String SPRINT_STARTED = "SPRINT_STARTED";
    public static final String SPRINT_COMPLETED = "SPRINT_COMPLETED";
    public static final String FILE_UPLOADED = "FILE_UPLOADED";
    public static final String FILE_DELETED = "FILE_DELETED";

    private AuditAction() {
    }
}
