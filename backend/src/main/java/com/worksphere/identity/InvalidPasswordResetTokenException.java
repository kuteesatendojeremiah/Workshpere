package com.worksphere.identity;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidPasswordResetTokenException extends ApiException {

    public InvalidPasswordResetTokenException() {
        super(
                ErrorCode.INVALID_PASSWORD_RESET_TOKEN,
                HttpStatus.BAD_REQUEST,
                "Password reset token is invalid, expired, or already used");
    }
}
