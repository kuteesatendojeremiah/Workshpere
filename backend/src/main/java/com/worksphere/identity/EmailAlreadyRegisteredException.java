package com.worksphere.identity;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class EmailAlreadyRegisteredException extends ApiException {

    public EmailAlreadyRegisteredException(String email) {
        super(
                ErrorCode.EMAIL_ALREADY_REGISTERED,
                HttpStatus.CONFLICT,
                "An account with email '" + email + "' already exists");
    }
}
