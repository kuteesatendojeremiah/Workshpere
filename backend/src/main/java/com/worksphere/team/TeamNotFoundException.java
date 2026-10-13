package com.worksphere.team;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown both when a team truly doesn't exist AND when it exists but belongs to a different
 * organization than the one in the URL - same "don't confirm existence" reasoning as
 * {@link com.worksphere.organization.OrganizationNotFoundException}.
 */
public class TeamNotFoundException extends ApiException {

    public TeamNotFoundException(UUID teamId) {
        super(ErrorCode.TEAM_NOT_FOUND, HttpStatus.NOT_FOUND, "No team found with id '" + teamId + "'");
    }
}
