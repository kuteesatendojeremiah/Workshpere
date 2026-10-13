package com.worksphere.task;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidTaskDependencyException extends ApiException {

    public InvalidTaskDependencyException(String reason) {
        super(ErrorCode.INVALID_TASK_DEPENDENCY, HttpStatus.BAD_REQUEST, reason);
    }
}
