package com.worksphere.organization;

import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditLog;
import com.worksphere.common.audit.AuditLogRepository;
import com.worksphere.common.audit.AuditLogSpecifications;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.Role;
import com.worksphere.common.security.RoleRepository;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

    private static final String VIEW_ORGANIZATION = "VIEW_ORGANIZATION";
    private static final String VIEW_AUDIT_LOGS = "VIEW_AUDIT_LOGS";

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RoleRepository roleRepository;
    private final AuditLogRepository auditLogRepository;
    private final UserService userService;
    private final PermissionChecker permissionChecker;
    private final AuditService auditService;

    public OrganizationService(
            OrganizationRepository organizationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            RoleRepository roleRepository,
            AuditLogRepository auditLogRepository,
            UserService userService,
            PermissionChecker permissionChecker,
            AuditService auditService) {
        this.organizationRepository = organizationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.roleRepository = roleRepository;
        this.auditLogRepository = auditLogRepository;
        this.userService = userService;
        this.permissionChecker = permissionChecker;
        this.auditService = auditService;
    }

    @Transactional
    public Organization createOrganization(UUID creatorUserId, String name) {
        User creator = userService.getProfile(creatorUserId);
        Role ownerRole = roleRepository
                .findByName(OrganizationRoleName.ORGANIZATION_OWNER.name())
                .orElseThrow(() -> new IllegalStateException(
                        "Seeded role ORGANIZATION_OWNER is missing - check V8__create_rbac_tables.sql"));

        Organization organization = organizationRepository.save(new Organization(name));
        organizationMemberRepository.save(new OrganizationMember(organization, creator, ownerRole));

        auditService.record(
                organization.getId(),
                creatorUserId,
                AuditAction.ORGANIZATION_CREATED,
                AuditResourceType.ORGANIZATION,
                organization.getId(),
                null);
        return organization;
    }

    @Transactional(readOnly = true)
    public Organization getOrganization(UUID organizationId, UUID callerUserId) {
        OrganizationMember membership = requireMembership(organizationId, callerUserId);
        permissionChecker.requirePermission(membership.getRole(), VIEW_ORGANIZATION);
        return membership.getOrganization();
    }

    /** The organization selector screen's data source - every org this user is actually a member of. */
    @Transactional(readOnly = true)
    public Page<Organization> listMyOrganizations(UUID callerUserId, Pageable pageable) {
        return organizationMemberRepository
                .findAllByUserId(callerUserId, pageable)
                .map(OrganizationMember::getOrganization);
    }

    @Transactional(readOnly = true)
    public Page<OrganizationMember> listMembers(UUID organizationId, UUID callerUserId, Pageable pageable) {
        OrganizationMember membership = requireMembership(organizationId, callerUserId);
        permissionChecker.requirePermission(membership.getRole(), VIEW_ORGANIZATION);
        return organizationMemberRepository.findAllByOrganizationId(organizationId, pageable);
    }

    /**
     * The tenant-isolation check: an organization id in a URL is just a lookup key, never a
     * trusted claim. Every org-scoped operation must prove - via a live membership row - that
     * this specific authenticated user belongs to this specific organization. A missing
     * membership and a nonexistent organization are deliberately indistinguishable to the
     * caller (see {@link OrganizationNotFoundException}). This is separate from, and happens
     * before, any permission check: membership answers "can they see this organization at
     * all", permissions answer "what are they allowed to do in it".
     */
    @Transactional(readOnly = true)
    public OrganizationMember requireMembership(UUID organizationId, UUID userId) {
        return organizationMemberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .orElseThrow(() -> new OrganizationNotFoundException(organizationId));
    }

    /** "Add GET audit-logs for owners" (build-prompts.md Step 15) - gated by VIEW_AUDIT_LOGS, seeded to ORGANIZATION_OWNER only. */
    @Transactional(readOnly = true)
    public Page<AuditLog> listAuditLogs(
            UUID organizationId, UUID callerUserId, String action, String resourceType, Pageable pageable) {
        OrganizationMember membership = requireMembership(organizationId, callerUserId);
        permissionChecker.requirePermission(membership.getRole(), VIEW_AUDIT_LOGS);

        var spec = AuditLogSpecifications.forOrganization(organizationId)
                .and(AuditLogSpecifications.hasAction(action))
                .and(AuditLogSpecifications.hasResourceType(resourceType));
        return auditLogRepository.findAll(spec, pageable);
    }
}
