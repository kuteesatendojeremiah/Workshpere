package com.worksphere.task;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** A task can only be assigned to someone who is actually a member of its project. */
public class UserNotInProjectException extends ApiException {

    public UserNotInProjectException(UUID userId) {
        super(
                ErrorCode.USER_NOT_IN_PROJECT,
                HttpStatus.BAD_REQUEST,
                "User '" + userId + "' is not a member of this task's project");
    }
}
