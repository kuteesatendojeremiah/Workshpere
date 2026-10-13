package com.worksphere.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.worksphere.common.audit.AuditLogRepository;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.Role;
import com.worksphere.common.security.RoleRepository;
import com.worksphere.common.security.TestRoles;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock
    private OrganizationRepository organizationRepository;

    @Mock
    private OrganizationMemberRepository organizationMemberRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private final PermissionChecker permissionChecker = new PermissionChecker();

    private OrganizationService organizationService;

    @BeforeEach
    void setUp() {
        organizationService = new OrganizationService(
                organizationRepository,
                organizationMemberRepository,
                roleRepository,
                auditLogRepository,
                userService,
                permissionChecker,
                auditService);
    }

    @Test
    void createOrganizationMakesCreatorTheOwner() {
        UUID creatorId = UUID.randomUUID();
        User creator = new User("owner@example.com", "hash", "Owner");
        Role ownerRole = TestRoles.role("ORGANIZATION_OWNER", "VIEW_ORGANIZATION", "MANAGE_ORGANIZATION_MEMBERS");
        when(userService.getProfile(creatorId)).thenReturn(creator);
        when(roleRepository.findByName("ORGANIZATION_OWNER")).thenReturn(Optional.of(ownerRole));
        when(organizationRepository.save(any(Organization.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Organization organization = organizationService.createOrganization(creatorId, "Acme Inc");

        assertThat(organization.getName()).isEqualTo("Acme Inc");
        verify(organizationMemberRepository)
                .save(ArgumentMatchers.argThat(
                        member -> member.getUser() == creator && member.getRole() == ownerRole));
    }

    @Test
    void requireMembershipThrowsWhenCallerIsNotAMember() {
        UUID organizationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, callerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> organizationService.requireMembership(organizationId, callerId))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void listMyOrganizationsReturnsOnlyOrganizationsTheCallerBelongsTo() {
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("member@example.com", "hash", "Member");
        Role memberRole = TestRoles.role("EMPLOYEE_MEMBER", "VIEW_ORGANIZATION");
        OrganizationMember membership = new OrganizationMember(organization, caller, memberRole);
        when(organizationMemberRepository.findAllByUserId(callerId, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(membership)));

        Page<Organization> result = organizationService.listMyOrganizations(callerId, Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(organization);
    }

    @Test
    void listMembersChecksMembershipBeforeQuerying() {
        UUID organizationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, callerId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(
                        () -> organizationService.listMembers(organizationId, callerId, Pageable.unpaged()))
                .isInstanceOf(OrganizationNotFoundException.class);
    }

    @Test
    void listMembersReturnsPageWhenCallerIsAMember() {
        UUID organizationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("member@example.com", "hash", "Member");
        Role memberRole = TestRoles.role("EMPLOYEE_MEMBER", "VIEW_ORGANIZATION");
        OrganizationMember membership = new OrganizationMember(organization, caller, memberRole);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, callerId))
                .thenReturn(Optional.of(membership));
        Page<OrganizationMember> page = new PageImpl<>(List.of(membership));
        when(organizationMemberRepository.findAllByOrganizationId(organizationId, Pageable.unpaged()))
                .thenReturn(page);

        Page<OrganizationMember> result =
                organizationService.listMembers(organizationId, callerId, Pageable.unpaged());

        assertThat(result.getContent()).containsExactly(membership);
    }

    @Test
    void listMembersRejectsRoleWithoutViewPermission() {
        UUID organizationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("no-view@example.com", "hash", "No View");
        Role roleWithoutView = TestRoles.role("PLATFORM_ADMIN");
        OrganizationMember membership = new OrganizationMember(organization, caller, roleWithoutView);
        when(organizationMemberRepository.findByOrganizationIdAndUserId(organizationId, callerId))
                .thenReturn(Optional.of(membership));

        assertThatThrownBy(
                        () -> organizationService.listMembers(organizationId, callerId, Pageable.unpaged()))
                .isInstanceOf(MissingPermissionException.class);
    }
}
