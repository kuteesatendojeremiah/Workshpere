package com.worksphere.sprint;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidSprintTaskException extends ApiException {

    public InvalidSprintTaskException(String reason) {
        super(ErrorCode.INVALID_SPRINT_TASK, HttpStatus.BAD_REQUEST, reason);
    }
}
