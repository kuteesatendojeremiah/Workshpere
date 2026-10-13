package com.worksphere.team;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.error.UserNotInOrganizationException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.common.security.Role;
import com.worksphere.common.security.TestRoles;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.organization.Organization;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationNotFoundException;
import com.worksphere.organization.OrganizationService;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeamServiceTest {

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private TeamMemberRepository teamMemberRepository;

    @Mock
    private OrganizationService organizationService;

    @Mock
    private UserService userService;

    @Mock
    private AuditService auditService;

    private final PermissionChecker permissionChecker = new PermissionChecker();
    private final Role ownerRole = TestRoles.role("ORGANIZATION_OWNER", "MANAGE_TEAMS");
    private final Role memberRole = TestRoles.role("EMPLOYEE_MEMBER");

    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(
                teamRepository, teamMemberRepository, organizationService, userService, permissionChecker, auditService);
    }

    @Test
    void createTeamRequiresManageTeamsPermission() {
        UUID organizationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("member@example.com", "hash", "Member");
        OrganizationMember membership = new OrganizationMember(organization, caller, memberRole);
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);

        assertThatThrownBy(() -> teamService.createTeam(organizationId, callerId, "Engineering"))
                .isInstanceOf(MissingPermissionException.class);
    }

    @Test
    void createTeamMakesCreatorTheLead() {
        UUID organizationId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("owner@example.com", "hash", "Owner");
        OrganizationMember membership = new OrganizationMember(organization, caller, ownerRole);
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);
        when(teamRepository.save(any(Team.class))).thenAnswer(inv -> inv.getArgument(0));

        Team team = teamService.createTeam(organizationId, callerId, "Engineering");

        assertThat(team.getName()).isEqualTo("Engineering");
        org.mockito.Mockito.verify(teamMemberRepository)
                .save(org.mockito.ArgumentMatchers.argThat(
                        tm -> tm.getUser() == caller && tm.getTeamRole() == TeamRole.LEAD));
    }

    @Test
    void getTeamFromDifferentOrganizationIsNotFound() {
        UUID organizationId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("member@example.com", "hash", "Member");
        OrganizationMember membership = new OrganizationMember(organization, caller, memberRole);
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);
        when(teamRepository.findByIdAndOrganizationId(teamId, organizationId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.getTeam(organizationId, teamId, callerId))
                .isInstanceOf(TeamNotFoundException.class);
    }

    @Test
    void addMemberRejectsUserOutsideOrganization() {
        UUID organizationId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("owner@example.com", "hash", "Owner");
        OrganizationMember membership = new OrganizationMember(organization, caller, ownerRole);
        Team team = new Team(organization, "Engineering");
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);
        when(teamRepository.findByIdAndOrganizationId(teamId, organizationId)).thenReturn(Optional.of(team));
        when(organizationService.requireMembership(organizationId, targetUserId))
                .thenThrow(new OrganizationNotFoundException(organizationId));

        assertThatThrownBy(
                        () -> teamService.addMember(organizationId, teamId, callerId, targetUserId, TeamRole.MEMBER))
                .isInstanceOf(UserNotInOrganizationException.class);
    }

    @Test
    void nonLeadNonManagerCannotAddMembers() {
        UUID organizationId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("member@example.com", "hash", "Member");
        OrganizationMember membership = new OrganizationMember(organization, caller, memberRole);
        Team team = new Team(organization, "Engineering");
        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(membership);
        when(teamRepository.findByIdAndOrganizationId(teamId, organizationId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, callerId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> teamService.addMember(
                        organizationId, teamId, callerId, UUID.randomUUID(), TeamRole.MEMBER))
                .isInstanceOf(MissingPermissionException.class);
    }

    @Test
    void teamLeadCanAddMembersWithoutOrgPermission() {
        UUID organizationId = UUID.randomUUID();
        UUID teamId = UUID.randomUUID();
        UUID callerId = UUID.randomUUID();
        UUID targetUserId = UUID.randomUUID();
        Organization organization = new Organization("Acme Inc");
        User caller = new User("lead@example.com", "hash", "Lead");
        User target = new User("target@example.com", "hash", "Target");
        OrganizationMember callerMembership = new OrganizationMember(organization, caller, memberRole);
        OrganizationMember targetMembership = new OrganizationMember(organization, target, memberRole);
        Team team = new Team(organization, "Engineering");
        TeamMember leadMembership = new TeamMember(team, caller, TeamRole.LEAD);

        when(organizationService.requireMembership(organizationId, callerId)).thenReturn(callerMembership);
        when(teamRepository.findByIdAndOrganizationId(teamId, organizationId)).thenReturn(Optional.of(team));
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, callerId)).thenReturn(Optional.of(leadMembership));
        when(organizationService.requireMembership(organizationId, targetUserId)).thenReturn(targetMembership);
        when(teamMemberRepository.findByTeamIdAndUserId(teamId, targetUserId)).thenReturn(Optional.empty());
        when(userService.getProfile(targetUserId)).thenReturn(target);
        when(teamRepository.getReferenceById(teamId)).thenReturn(team);
        when(teamMemberRepository.save(any(TeamMember.class))).thenAnswer(inv -> inv.getArgument(0));

        TeamMember result = teamService.addMember(organizationId, teamId, callerId, targetUserId, TeamRole.MEMBER);

        assertThat(result.getUser()).isEqualTo(target);
        assertThat(result.getTeamRole()).isEqualTo(TeamRole.MEMBER);
    }
}
