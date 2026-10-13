package com.worksphere.common.error;

import org.springframework.http.HttpStatus;

/** A query/path parameter was present but unusable (non-numeric page, unknown sort field, ...). */
public class InvalidRequestParameterException extends ApiException {

    public InvalidRequestParameterException(String message) {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }
}
