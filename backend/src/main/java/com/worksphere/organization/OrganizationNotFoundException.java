package com.worksphere.organization;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown both when an organization truly doesn't exist AND when the caller isn't a member of
 * one that does - deliberately indistinguishable, so a response never confirms an organization's
 * existence to someone outside it. This is the tenant-isolation check: every org-scoped request
 * must prove live membership, not just supply an id.
 */
public class OrganizationNotFoundException extends ApiException {

    public OrganizationNotFoundException(UUID organizationId) {
        super(
                ErrorCode.ORGANIZATION_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "No organization found with id '" + organizationId + "'");
    }
}
