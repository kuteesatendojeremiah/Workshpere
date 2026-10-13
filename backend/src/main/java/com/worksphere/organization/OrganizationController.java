package com.worksphere.organization;

import com.worksphere.common.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OrganizationController {

    private final OrganizationService organizationService;

    public OrganizationController(OrganizationService organizationService) {
        this.organizationService = organizationService;
    }

    @PostMapping("/api/v1/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public OrganizationResponse create(
            @AuthenticationPrincipal UUID callerUserId, @Valid @RequestBody CreateOrganizationRequest request) {
        Organization organization = organizationService.createOrganization(callerUserId, request.name());
        return OrganizationResponse.from(organization);
    }

    @GetMapping("/api/v1/organizations")
    public PageResponse<OrganizationResponse> listMine(
            @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(
                organizationService.listMyOrganizations(callerUserId, pageable).map(OrganizationResponse::from));
    }

    @GetMapping("/api/v1/organizations/{organizationId}")
    public OrganizationResponse get(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UUID callerUserId) {
        return OrganizationResponse.from(organizationService.getOrganization(organizationId, callerUserId));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/members")
    public PageResponse<OrganizationMemberResponse> listMembers(
            @PathVariable UUID organizationId, @AuthenticationPrincipal UUID callerUserId, Pageable pageable) {
        return PageResponse.from(organizationService
                .listMembers(organizationId, callerUserId, pageable)
                .map(OrganizationMemberResponse::from));
    }

    @GetMapping("/api/v1/organizations/{organizationId}/audit-logs")
    public PageResponse<AuditLogResponse> listAuditLogs(
            @PathVariable UUID organizationId,
            @AuthenticationPrincipal UUID callerUserId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType,
            Pageable pageable) {
        return PageResponse.from(organizationService
                .listAuditLogs(organizationId, callerUserId, action, resourceType, pageable)
                .map(AuditLogResponse::from));
    }
}
