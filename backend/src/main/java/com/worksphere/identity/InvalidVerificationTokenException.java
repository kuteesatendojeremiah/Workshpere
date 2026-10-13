package com.worksphere.identity;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidVerificationTokenException extends ApiException {

    public InvalidVerificationTokenException() {
        super(
                ErrorCode.INVALID_VERIFICATION_TOKEN,
                HttpStatus.BAD_REQUEST,
                "Verification token is invalid, expired, or already used");
    }
}
