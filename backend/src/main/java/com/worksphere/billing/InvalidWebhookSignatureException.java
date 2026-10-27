package com.worksphere.billing;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** Deliberately says nothing about WHY the signature was refused. */
public class InvalidWebhookSignatureException extends ApiException {

    public InvalidWebhookSignatureException() {
        super(ErrorCode.WEBHOOK_SIGNATURE_INVALID, HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
    }
}
