package com.worksphere.sprint;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidSprintStateException extends ApiException {

    public InvalidSprintStateException(String reason) {
        super(ErrorCode.INVALID_SPRINT_STATE, HttpStatus.BAD_REQUEST, reason);
    }
}
