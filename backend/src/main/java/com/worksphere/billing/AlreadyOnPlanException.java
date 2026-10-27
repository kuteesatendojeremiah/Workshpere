package com.worksphere.billing;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class AlreadyOnPlanException extends ApiException {

    public AlreadyOnPlanException(String planName) {
        super(ErrorCode.ALREADY_ON_PLAN, HttpStatus.CONFLICT, "The organization is already on the " + planName + " plan");
    }
}
