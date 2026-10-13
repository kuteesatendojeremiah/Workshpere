package com.worksphere.identity;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class UserNotFoundException extends ApiException {

    public UserNotFoundException(UUID userId) {
        super(ErrorCode.USER_NOT_FOUND, HttpStatus.NOT_FOUND, "No user found with id '" + userId + "'");
    }
}
