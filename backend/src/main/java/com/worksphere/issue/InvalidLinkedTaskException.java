package com.worksphere.issue;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidLinkedTaskException extends ApiException {

    public InvalidLinkedTaskException() {
        super(
                ErrorCode.INVALID_LINKED_TASK,
                HttpStatus.BAD_REQUEST,
                "The linked task must belong to the same project as the issue");
    }
}
