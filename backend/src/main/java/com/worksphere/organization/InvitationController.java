package com.worksphere.organization;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InvitationController {

    private final InvitationService invitationService;

    public InvitationController(InvitationService invitationService) {
        this.invitationService = invitationService;
    }

    @PostMapping("/api/v1/organizations/{organizationId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public InvitationResponse invite(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal UUID callerUserId,
            @Valid @RequestBody InviteMemberRequest request) {
        Invitation invitation =
                invitationService.invite(organizationId, callerUserId, request.email(), request.role());
        return InvitationResponse.from(invitation);
    }

    @PostMapping("/api/v1/invitations/accept")
    public OrganizationMemberResponse accept(
            @AuthenticationPrincipal UUID callerUserId, @Valid @RequestBody AcceptInvitationRequest request) {
        OrganizationMember member = invitationService.acceptInvitation(request.token(), callerUserId);
        return OrganizationMemberResponse.from(member);
    }
}
