package com.worksphere.billing;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** The signature was valid but the body is not an event we understand. */
public class MalformedWebhookException extends ApiException {

    public MalformedWebhookException() {
        super(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, "Malformed webhook body");
    }
}
