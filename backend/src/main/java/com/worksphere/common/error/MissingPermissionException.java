package com.worksphere.common.error;

import org.springframework.http.HttpStatus;

/** Thrown when a caller is a legitimate, authenticated party but lacks a specific permission. */
public class MissingPermissionException extends ApiException {

    public MissingPermissionException(String permissionKey) {
        super(
                ErrorCode.MISSING_PERMISSION,
                HttpStatus.FORBIDDEN,
                "Missing required permission: " + permissionKey);
    }
}
