package com.worksphere.organization;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class InvalidInvitationTokenException extends ApiException {

    public InvalidInvitationTokenException() {
        super(
                ErrorCode.INVALID_INVITATION_TOKEN,
                HttpStatus.BAD_REQUEST,
                "Invitation token is invalid, expired, already accepted, or not addressed to this account");
    }
}
