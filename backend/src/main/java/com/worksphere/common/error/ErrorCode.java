package com.worksphere.common.error;

public final class ErrorCode {

    public static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";
    public static final String EMAIL_ALREADY_REGISTERED = "EMAIL_ALREADY_REGISTERED";
    public static final String USER_NOT_FOUND = "USER_NOT_FOUND";
    public static final String INVALID_VERIFICATION_TOKEN = "INVALID_VERIFICATION_TOKEN";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String UNAUTHENTICATED = "UNAUTHENTICATED";
    public static final String ACCESS_DENIED = "ACCESS_DENIED";
    public static final String INVALID_REFRESH_TOKEN = "INVALID_REFRESH_TOKEN";
    public static final String INVALID_PASSWORD_RESET_TOKEN = "INVALID_PASSWORD_RESET_TOKEN";
    public static final String ORGANIZATION_NOT_FOUND = "ORGANIZATION_NOT_FOUND";
    public static final String INVALID_INVITATION_TOKEN = "INVALID_INVITATION_TOKEN";
    public static final String MISSING_PERMISSION = "MISSING_PERMISSION";
    public static final String INVALID_ROLE = "INVALID_ROLE";
    public static final String TEAM_NOT_FOUND = "TEAM_NOT_FOUND";
    public static final String USER_NOT_IN_ORGANIZATION = "USER_NOT_IN_ORGANIZATION";
    public static final String PROJECT_NOT_FOUND = "PROJECT_NOT_FOUND";
    public static final String TASK_NOT_FOUND = "TASK_NOT_FOUND";
    public static final String USER_NOT_IN_PROJECT = "USER_NOT_IN_PROJECT";
    public static final String INVALID_TASK_DEPENDENCY = "INVALID_TASK_DEPENDENCY";
    public static final String STALE_VERSION = "STALE_VERSION";
    public static final String COMMENT_NOT_FOUND = "COMMENT_NOT_FOUND";
    public static final String LABEL_NOT_FOUND = "LABEL_NOT_FOUND";
    public static final String DUPLICATE_LABEL_NAME = "DUPLICATE_LABEL_NAME";
    public static final String INVALID_STATUS_TRANSITION = "INVALID_STATUS_TRANSITION";
    public static final String SPRINT_NOT_FOUND = "SPRINT_NOT_FOUND";
    public static final String INVALID_SPRINT_STATE = "INVALID_SPRINT_STATE";
    public static final String INVALID_SPRINT_TASK = "INVALID_SPRINT_TASK";
    public static final String ISSUE_NOT_FOUND = "ISSUE_NOT_FOUND";
    public static final String INVALID_LINKED_TASK = "INVALID_LINKED_TASK";
    public static final String NOTIFICATION_NOT_FOUND = "NOTIFICATION_NOT_FOUND";
    public static final String RATE_LIMITED = "RATE_LIMITED";
    public static final String FILE_NOT_FOUND = "FILE_NOT_FOUND";
    public static final String INVALID_FILE = "INVALID_FILE";
    public static final String FILE_TOO_LARGE = "FILE_TOO_LARGE";
    public static final String STORAGE_UNAVAILABLE = "STORAGE_UNAVAILABLE";

    private ErrorCode() {
    }
}
