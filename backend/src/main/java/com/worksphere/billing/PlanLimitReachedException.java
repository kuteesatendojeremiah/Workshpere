package com.worksphere.billing;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** HTTP 402 Payment Required: the organization's plan does not allow more of something. */
public class PlanLimitReachedException extends ApiException {

    public PlanLimitReachedException(String what, int limit, String planName) {
        super(
                ErrorCode.PLAN_LIMIT_REACHED,
                HttpStatus.PAYMENT_REQUIRED,
                "The " + planName + " plan allows at most " + limit + " " + what + " - upgrade to add more");
    }
}
