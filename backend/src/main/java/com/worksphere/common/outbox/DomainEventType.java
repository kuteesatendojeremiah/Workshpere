package com.worksphere.common.outbox;

/** The events from spec section 16.1. The name is the stable contract with consumers. */
public enum DomainEventType {
    USER_REGISTERED,
    ORGANIZATION_CREATED,
    MEMBER_INVITED,
    PROJECT_CREATED,
    TASK_CREATED,
    TASK_ASSIGNED,
    TASK_STATUS_CHANGED,
    COMMENT_CREATED,
    ISSUE_CREATED,
    SPRINT_STARTED,
    SPRINT_COMPLETED,
    SUBSCRIPTION_UPDATED
}
