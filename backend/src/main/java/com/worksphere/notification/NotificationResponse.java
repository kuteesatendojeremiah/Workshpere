package com.worksphere.notification;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id, NotificationType type, String message, String resourceType, UUID resourceId, boolean read, Instant createdAt) {

    public static NotificationResponse from(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                notification.getMessage(),
                notification.getResourceType(),
                notification.getResourceId(),
                notification.isRead(),
                notification.getCreatedAt());
    }
}
