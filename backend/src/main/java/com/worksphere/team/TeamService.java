package com.worksphere.team;

import com.worksphere.common.audit.AuditAction;
import com.worksphere.common.audit.AuditResourceType;
import com.worksphere.common.audit.AuditService;
import com.worksphere.common.error.MissingPermissionException;
import com.worksphere.common.error.UserNotInOrganizationException;
import com.worksphere.common.security.PermissionChecker;
import com.worksphere.identity.User;
import com.worksphere.identity.UserService;
import com.worksphere.organization.OrganizationMember;
import com.worksphere.organization.OrganizationNotFoundException;
import com.worksphere.organization.OrganizationService;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {

    private static final String MANAGE_TEAMS = "MANAGE_TEAMS";

    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final OrganizationService organizationService;
    private final UserService userService;
    private final PermissionChecker permissionChecker;
    private final AuditService auditService;

    public TeamService(
            TeamRepository teamRepository,
            TeamMemberRepository teamMemberRepository,
            OrganizationService organizationService,
            UserService userService,
            PermissionChecker permissionChecker,
            AuditService auditService) {
        this.teamRepository = teamRepository;
        this.teamMemberRepository = teamMemberRepository;
        this.organizationService = organizationService;
        this.userService = userService;
        this.permissionChecker = permissionChecker;
        this.auditService = auditService;
    }

    @Transactional
    public Team createTeam(UUID organizationId, UUID callerUserId, String name) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        permissionChecker.requirePermission(callerMembership.getRole(), MANAGE_TEAMS);

        Team team = teamRepository.save(new Team(callerMembership.getOrganization(), name));
        teamMemberRepository.save(new TeamMember(team, callerMembership.getUser(), TeamRole.LEAD));

        auditService.record(
                organizationId, callerUserId, AuditAction.TEAM_CREATED, AuditResourceType.TEAM, team.getId(), null);
        return team;
    }

    @Transactional(readOnly = true)
    public Team getTeam(UUID organizationId, UUID teamId, UUID callerUserId) {
        organizationService.requireMembership(organizationId, callerUserId);
        return requireTeamInOrganization(organizationId, teamId);
    }

    @Transactional(readOnly = true)
    public Page<Team> listTeams(UUID organizationId, UUID callerUserId, Pageable pageable) {
        organizationService.requireMembership(organizationId, callerUserId);
        return teamRepository.findAllByOrganizationId(organizationId, pageable);
    }

    @Transactional
    public Team renameTeam(UUID organizationId, UUID teamId, UUID callerUserId, String newName) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        Team team = requireTeamInOrganization(organizationId, teamId);
        requireTeamManagementRights(teamId, callerUserId, callerMembership);

        team.rename(newName);
        return team;
    }

    @Transactional
    public void deleteTeam(UUID organizationId, UUID teamId, UUID callerUserId) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        Team team = requireTeamInOrganization(organizationId, teamId);
        permissionChecker.requirePermission(callerMembership.getRole(), MANAGE_TEAMS);

        teamRepository.delete(team);
        auditService.record(
                organizationId, callerUserId, AuditAction.TEAM_DELETED, AuditResourceType.TEAM, teamId, null);
    }

    @Transactional
    public TeamMember addMember(UUID organizationId, UUID teamId, UUID callerUserId, UUID targetUserId, TeamRole role) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        requireTeamInOrganization(organizationId, teamId);
        requireTeamManagementRights(teamId, callerUserId, callerMembership);

        try {
            organizationService.requireMembership(organizationId, targetUserId);
        } catch (OrganizationNotFoundException e) {
            throw new UserNotInOrganizationException(targetUserId);
        }

        TeamMember member = teamMemberRepository
                .findByTeamIdAndUserId(teamId, targetUserId)
                .orElseGet(() -> {
                    User targetUser = userService.getProfile(targetUserId);
                    Team team = teamRepository.getReferenceById(teamId);
                    return teamMemberRepository.save(new TeamMember(team, targetUser, role));
                });

        auditService.record(
                organizationId, callerUserId, AuditAction.TEAM_MEMBER_ADDED, AuditResourceType.TEAM, teamId, null);
        return member;
    }

    @Transactional
    public void removeMember(UUID organizationId, UUID teamId, UUID callerUserId, UUID targetUserId) {
        OrganizationMember callerMembership = organizationService.requireMembership(organizationId, callerUserId);
        requireTeamInOrganization(organizationId, teamId);
        requireTeamManagementRights(teamId, callerUserId, callerMembership);

        teamMemberRepository.deleteByTeamIdAndUserId(teamId, targetUserId);
        auditService.record(
                organizationId, callerUserId, AuditAction.TEAM_MEMBER_REMOVED, AuditResourceType.TEAM, teamId, null);
    }

    @Transactional(readOnly = true)
    public Page<TeamMember> listMembers(UUID organizationId, UUID teamId, UUID callerUserId, Pageable pageable) {
        organizationService.requireMembership(organizationId, callerUserId);
        requireTeamInOrganization(organizationId, teamId);
        return teamMemberRepository.findAllByTeamId(teamId, pageable);
    }

    private Team requireTeamInOrganization(UUID organizationId, UUID teamId) {
        return teamRepository
                .findByIdAndOrganizationId(teamId, organizationId)
                .orElseThrow(() -> new TeamNotFoundException(teamId));
    }

    private void requireTeamManagementRights(UUID teamId, UUID callerUserId, OrganizationMember callerMembership) {
        if (permissionChecker.hasPermission(callerMembership.getRole(), MANAGE_TEAMS)) {
            return;
        }

        boolean isTeamLead = teamMemberRepository
                .findByTeamIdAndUserId(teamId, callerUserId)
                .map(member -> member.getTeamRole() == TeamRole.LEAD)
                .orElse(false);
        if (!isTeamLead) {
            throw new MissingPermissionException(MANAGE_TEAMS);
        }
    }
}
