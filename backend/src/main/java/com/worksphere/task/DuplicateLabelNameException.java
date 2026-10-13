package com.worksphere.task;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class DuplicateLabelNameException extends ApiException {

    public DuplicateLabelNameException(String name) {
        super(ErrorCode.DUPLICATE_LABEL_NAME, HttpStatus.CONFLICT, "A label named '" + name + "' already exists in this project");
    }
}
