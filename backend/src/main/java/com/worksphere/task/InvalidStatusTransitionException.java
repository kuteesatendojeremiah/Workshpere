package com.worksphere.task;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidStatusTransitionException extends ApiException {

    public InvalidStatusTransitionException(TaskStatus from, TaskStatus to) {
        super(
                ErrorCode.INVALID_STATUS_TRANSITION,
                HttpStatus.BAD_REQUEST,
                "Cannot move a task from " + from + " directly to " + to);
    }
}
