package com.worksphere.common.error;

import org.springframework.http.HttpStatus;

/** The client's update was based on a version of the resource that's no longer current. */
public class StaleVersionException extends ApiException {

    public StaleVersionException() {
        super(
                ErrorCode.STALE_VERSION,
                HttpStatus.CONFLICT,
                "This resource was modified by someone else - reload it and try again");
    }
}
