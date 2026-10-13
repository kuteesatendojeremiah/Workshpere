package com.worksphere.identity;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidRefreshTokenException extends ApiException {

    public InvalidRefreshTokenException() {
        super(
                ErrorCode.INVALID_REFRESH_TOKEN,
                HttpStatus.UNAUTHORIZED,
                "Refresh token is invalid, expired, or already used");
    }
}
