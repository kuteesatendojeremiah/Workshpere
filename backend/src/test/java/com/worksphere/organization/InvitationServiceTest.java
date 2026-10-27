package com.worksphere.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.Role;
import com.worksphere.common.security.RoleRepository;
import com.worksphere.common.security.TestRoles;
import com.worksphere.identity.SecureTokenGenerator;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InvitationServiceTest {

    @Mock
    private com.worksphere.billing.BillingService billingService;

    @Mock
    private com.worksphere.common.mail.EmailService emailService;

    @Mock
    private com.worksphere.common.outbox.OutboxService outboxService;

    @Mock
    private InvitationRepository invitationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private final PermissionChecker permissionChecker = new PermissionChecker();
    private final Role ownerRole = TestRoles.role("ORGANIZATION_OWNER", "VIEW_ORGANIZATION", "MANAGE_ORGANIZATION_MEMBERS");
    private final Role memberRole = TestRoles.role("EMPLOYEE_MEMBER", "VIEW_ORGANIZATION");

    private InvitationService invitationService;

    @BeforeEach
    void setUp() {
        invitationService = new InvitationService(
                invitationRepository,
                organizationMemberRepository,
                roleRepository,
                organizationService,
                userService,
                new SecureTokenGenerator(),
                permissionChecker,
                auditService, outboxService, emailService, billingService);
    }

    @Test
    void inviteRejectsNonOwner() {
        UUID organizationId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User inviter = new User("member@example.com", "hash", "Member");
        OrganizationMember membership = new OrganizationMember(organization, inviter, memberRole);
        when(organizationService.requireMembership(organizationId, inviterId)).thenReturn(membership);

        assertThatThrownBy(() -> invitationService.invite(organizationId, inviterId, "new@example.com", null))
                .isInstanceOf(MissingPermissionException.class);
    }

    @Test
    void inviteSucceedsForOwnerAndDefaultsToMemberRole() {
        UUID organizationId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User owner = new User("owner@example.com", "hash", "Owner");
        OrganizationMember membership = new OrganizationMember(organization, owner, ownerRole);
        when(organizationService.requireMembership(organizationId, inviterId)).thenReturn(membership);
        when(roleRepository.findByName("EMPLOYEE_MEMBER")).thenReturn(Optional.of(memberRole));

        Invitation invitation = invitationService.invite(organizationId, inviterId, "new@example.com", null);

        assertThat(invitation.getEmail()).isEqualTo("new@example.com");
        assertThat(invitation.getRole()).isEqualTo(memberRole);
    }

    @Test
    void inviteRejectsRoleNameThatIsNotOrganizationAssignable() {
        UUID organizationId = UUID.randomUUID();
        UUID inviterId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User owner = new User("owner@example.com", "hash", "Owner");
        OrganizationMember membership = new OrganizationMember(organization, owner, ownerRole);
        when(organizationService.requireMembership(organizationId, inviterId)).thenReturn(membership);

        assertThatThrownBy(() ->
                        invitationService.invite(organizationId, inviterId, "new@example.com", "PLATFORM_ADMIN"))
                .isInstanceOf(InvalidOrganizationRoleException.class);
    }

    @Test
    void acceptInvitationRejectsUnknownToken() {
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invitationService.acceptInvitation("not-a-real-token", UUID.randomUUID()))
                .isInstanceOf(InvalidInvitationTokenException.class);
    }

    @Test
    void acceptInvitationRejectsExpiredInvitation() {
        Organization organization = new Organization("Acme Inc");
        User owner = new User("owner@example.com", "hash", "Owner");
        Invitation invitation = new Invitation(
                organization,
                "invited@example.com",
                memberRole,
                owner,
                "irrelevant-hash",
                Instant.now().minusSeconds(60));
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));

        assertThatThrownBy(() -> invitationService.acceptInvitation("expired-token", UUID.randomUUID()))
                .isInstanceOf(InvalidInvitationTokenException.class);
    }

    @Test
    void acceptInvitationRejectsWhenAccountEmailDoesNotMatchInvitedEmail() {
        Organization organization = new Organization("Acme Inc");
        User owner = new User("owner@example.com", "hash", "Owner");
        Invitation invitation = new Invitation(
                organization,
                "invited@example.com",
                memberRole,
                owner,
                "irrelevant-hash",
                Instant.now().plusSeconds(3600));
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));

        UUID acceptingUserId = UUID.randomUUID();
        User differentAccount = new User("someone-else@example.com", "hash", "Someone Else");
        when(userService.getProfile(acceptingUserId)).thenReturn(differentAccount);

        assertThatThrownBy(() -> invitationService.acceptInvitation("raw-token", acceptingUserId))
                .isInstanceOf(InvalidInvitationTokenException.class);
    }

    @Test
    void acceptInvitationCreatesMembershipWhenEmailMatches() {
        Organization organization = new Organization("Acme Inc");
        User owner = new User("owner@example.com", "hash", "Owner");
        Invitation invitation = new Invitation(
                organization,
                "invited@example.com",
                memberRole,
                owner,
                "irrelevant-hash",
                Instant.now().plusSeconds(3600));
        when(invitationRepository.findByTokenHash(any())).thenReturn(Optional.of(invitation));

        UUID acceptingUserId = UUID.randomUUID();
        User acceptingUser = new User("invited@example.com", "hash", "Invited User");
        when(userService.getProfile(acceptingUserId)).thenReturn(acceptingUser);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organization.getId(), acceptingUserId))
                .thenReturn(Optional.empty());
        when(organizationMemberRepository.save(any(OrganizationMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        OrganizationMember result = invitationService.acceptInvitation("raw-token", acceptingUserId);

        assertThat(result.getUser()).isEqualTo(acceptingUser);
        assertThat(result.getRole()).isEqualTo(memberRole);
        assertThat(invitation.isAccepted()).isTrue();
    }
}
