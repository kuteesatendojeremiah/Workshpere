package com.worksphere.organization;

import com.worksphere.common.audit.AuditLog;
import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
        UUID id,
        UUID actorId,
        String action,
        String resourceType,
        UUID resourceId,
        String metadata,
        String requestId,
        Instant createdAt) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getActorId(),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getMetadata(),
                log.getRequestId(),
                log.getCreatedAt());
    }
}
