package com.worksphere.common.error;

import java.util.UUID;
import org.springframework.http.HttpStatus;

/** A referenced user (e.g. being added to a team or project) isn't a member of the organization. */
public class UserNotInOrganizationException extends ApiException {

    public UserNotInOrganizationException(UUID userId) {
        super(
                ErrorCode.USER_NOT_IN_ORGANIZATION,
                HttpStatus.BAD_REQUEST,
                "User '" + userId + "' is not a member of this organization");
    }
}
