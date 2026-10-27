package com.worksphere.organization;

import com.worksphere.common.mail.EmailService;
import com.worksphere.common.outbox.DomainEventType;
import com.worksphere.common.outbox.OutboxService;
import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.Role;
import com.worksphere.common.security.RoleRepository;
import com.worksphere.identity.SecureTokenGenerator;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvitationService {

    private static final Logger log = LoggerFactory.getLogger(InvitationService.class);
    private static final Duration INVITATION_TOKEN_TTL = Duration.ofDays(7);
    private static final String MANAGE_ORGANIZATION_MEMBERS = "MANAGE_ORGANIZATION_MEMBERS";

    private final InvitationRepository invitationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final RoleRepository roleRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final SecureTokenGenerator tokenGenerator;
    private final PermissionChecker permissionChecker;
    private final OutboxService outboxService;
    private final AuditService auditService;
    private final EmailService emailService;

    public InvitationService(
            InvitationRepository invitationRepository,
            OrganizationMemberRepository organizationMemberRepository,
            RoleRepository roleRepository,
            OrganizationService organizationService,
            UserService userService,
            SecureTokenGenerator tokenGenerator,
            PermissionChecker permissionChecker,
            AuditService auditService,
            OutboxService outboxService,
            EmailService emailService) {
        this.invitationRepository = invitationRepository;
        this.organizationMemberRepository = organizationMemberRepository;
        this.roleRepository = roleRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.tokenGenerator = tokenGenerator;
        this.permissionChecker = permissionChecker;
        this.auditService = auditService;
        this.outboxService = outboxService;
        this.emailService = emailService;
    }

    @Transactional
    public Invitation invite(UUID organizationId, UUID inviterUserId, String email, String roleName) {
        OrganizationMember inviterMembership = organizationService.requireMembership(organizationId, inviterUserId);
        permissionChecker.requirePermission(inviterMembership.getRole(), MANAGE_ORGANIZATION_MEMBERS);

        Role invitedRole = resolveAssignableRole(roleName);
        String rawToken = tokenGenerator.generateRawToken();
        Invitation invitation = new Invitation(
                inviterMembership.getOrganization(),
                email,
                invitedRole,
                inviterMembership.getUser(),
                tokenGenerator.hash(rawToken),
                Instant.now().plus(INVITATION_TOKEN_TTL));
        invitationRepository.save(invitation);

        emailService.sendInvitation(email, inviterMembership.getOrganization().getName(), rawToken);

        auditService.record(
                organizationId,
                inviterUserId,
                AuditAction.MEMBER_INVITED,
                AuditResourceType.INVITATION,
                invitation.getId(),
                Map.of("email", email));
        outboxService.record(
                DomainEventType.MEMBER_INVITED,
                "INVITATION",
                invitation.getId(),
                organizationId,
                java.util.Map.of("invitedBy", String.valueOf(inviterUserId)));

        return invitation;
    }

    @Transactional
    public OrganizationMember acceptInvitation(String rawToken, UUID acceptingUserId) {
        Invitation invitation = invitationRepository
                .findByTokenHash(tokenGenerator.hash(rawToken))
                .orElseThrow(InvalidInvitationTokenException::new);

        if (invitation.isAccepted() || invitation.isExpired()) {
            throw new InvalidInvitationTokenException();
        }

        User acceptingUser = userService.getProfile(acceptingUserId);
        if (!invitation.getEmail().equalsIgnoreCase(acceptingUser.getEmail())) {
            throw new InvalidInvitationTokenException();
        }

        invitation.markAccepted();

        OrganizationMember member = organizationMemberRepository
                .findByOrganizationIdAndUserId(invitation.getOrganization().getId(), acceptingUserId)
                .orElseGet(() -> organizationMemberRepository.save(
                        new OrganizationMember(invitation.getOrganization(), acceptingUser, invitation.getRole())));

        auditService.record(
                invitation.getOrganization().getId(),
                acceptingUserId,
                AuditAction.INVITATION_ACCEPTED,
                AuditResourceType.INVITATION,
                invitation.getId(),
                null);
        return member;
    }

    private Role resolveAssignableRole(String roleName) {
        String effectiveName =
                roleName != null ? roleName : OrganizationRoleName.EMPLOYEE_MEMBER.name();

        boolean isAssignable = Arrays.stream(OrganizationRoleName.values())
                .anyMatch(assignable -> assignable.name().equals(effectiveName));
        if (!isAssignable) {
            throw new InvalidOrganizationRoleException(effectiveName);
        }

        return roleRepository
                .findByName(effectiveName)
                .orElseThrow(() -> new IllegalStateException(
                        "Seeded role " + effectiveName + " is missing - check V8__create_rbac_tables.sql"));
    }
}
