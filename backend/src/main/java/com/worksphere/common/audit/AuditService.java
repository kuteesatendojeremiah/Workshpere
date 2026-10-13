package com.worksphere.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.worksphere.common.web.CorrelationIdHolder;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place every module calls to record a security- or business-sensitive action -
 * callers pass plain ids and a free-form metadata map, never entities, which is what keeps this
 * {@code common} service from needing to depend on any higher-level module.
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public AuditService(AuditLogRepository auditLogRepository, ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void record(
            UUID organizationId, UUID actorId, String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        String metadataJson = toJson(metadata);
        AuditLog entry = new AuditLog(
                organizationId, actorId, action, resourceType, resourceId, metadataJson, CorrelationIdHolder.get());
        auditLogRepository.save(entry);
    }

    private String toJson(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("Failed to serialize audit log metadata, storing without it", e);
            return null;
        }
    }
}
