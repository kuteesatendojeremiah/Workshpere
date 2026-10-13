package com.worksphere.organization;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidOrganizationRoleException extends ApiException {

    public InvalidOrganizationRoleException(String role) {
        super(ErrorCode.INVALID_ROLE, HttpStatus.BAD_REQUEST, "'" + role + "' is not a valid organization role");
    }
}
