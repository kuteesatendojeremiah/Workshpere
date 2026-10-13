package com.worksphere.project;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a project truly doesn't exist, belongs to a different organization than the URL
 * says, or exists but the caller has no right to see it (not a privileged role and not a
 * project member) - all deliberately indistinguishable to the caller.
 */
public class ProjectNotFoundException extends ApiException {

    public ProjectNotFoundException(UUID projectId) {
        super(ErrorCode.PROJECT_NOT_FOUND, HttpStatus.NOT_FOUND, "No project found with id '" + projectId + "'");
    }
}
