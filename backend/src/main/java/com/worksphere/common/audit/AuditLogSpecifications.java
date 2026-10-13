package com.worksphere.common.audit;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

public final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    public static Specification<AuditLog> forOrganization(UUID organizationId) {
        return (root, query, cb) -> cb.equal(root.get("organizationId"), organizationId);
    }

    public static Specification<AuditLog> hasAction(String action) {
        return (root, query, cb) -> action == null ? null : cb.equal(root.get("action"), action);
    }

    public static Specification<AuditLog> hasResourceType(String resourceType) {
        return (root, query, cb) -> resourceType == null ? null : cb.equal(root.get("resourceType"), resourceType);
    }
}
