package com.worksphere.team;

import com.worksphere.common.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TeamController {

    private final TeamService teamService;

    public TeamController(TeamService teamService) {
        this.teamService = teamService;
    }

    @PostMapping("/api/v1/organizations/{organizationId}/teams")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamResponse create(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody CreateTeamRequest request) {
        return TeamResponse.from(teamService.createTeam(organizationId, callerUserId, request.name()));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/teams/{teamId}")
    public TeamResponse get(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @AuthenticationPrincipal UUID callerUserId) {
        return TeamResponse.from(teamService.getTeam(organizationId, teamId, callerUserId));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/teams")
    public PageResponse<TeamResponse> list(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(teamService.listTeams(organizationId, callerUserId, pageable).map(TeamResponse::from));
    }

    @PutMapping("/api/v1/organizations/{organizationId}/teams/{teamId}")
    public TeamResponse rename(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody UpdateTeamRequest request) {
        return TeamResponse.from(teamService.renameTeam(organizationId, teamId, callerUserId, request.name()));
    }

    @DeleteMapping("/api/v1/organizations/{organizationId}/teams/{teamId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @AuthenticationPrincipal UUID callerUserId) {
        teamService.deleteTeam(organizationId, teamId, callerUserId);
    }

    @PostMapping("/api/v1/organizations/{organizationId}/teams/{teamId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public TeamMemberResponse addMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody AddTeamMemberRequest request) {
        return TeamMemberResponse.from(teamService.addMember(
                organizationId, teamId, callerUserId, request.userId(), request.teamRoleOrDefault()));
    }

    @DeleteMapping("/api/v1/organizations/{organizationId}/teams/{teamId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @PathVariable UUID userId,
            @AuthenticationPrincipal UUID callerUserId) {
        teamService.removeMember(organizationId, teamId, callerUserId, userId);
    }

    @GetMapping("/api/v1/organizations/{organizationId}/teams/{teamId}/members")
    public PageResponse<TeamMemberResponse> listMembers(
            @PathVariable UUID organizationId,
            @PathVariable UUID teamId,
            @AuthenticationPrincipal UUID callerUserId,
            Pageable pageable) {
        return PageResponse.from(teamService
                .listMembers(organizationId, teamId, callerUserId, pageable)
                .map(TeamMemberResponse::from));
    }
}
