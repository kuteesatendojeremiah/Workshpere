package com.worksphere.billing;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class PlanNotFoundException extends ApiException {

    public PlanNotFoundException(String planKey) {
        super(ErrorCode.PLAN_NOT_FOUND, HttpStatus.NOT_FOUND, "Unknown plan: " + planKey);
    }
}
