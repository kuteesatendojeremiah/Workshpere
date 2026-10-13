package com.worksphere.issue;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class IssueNotFoundException extends ApiException {

    public IssueNotFoundException(UUID issueId) {
        super(ErrorCode.ISSUE_NOT_FOUND, HttpStatus.NOT_FOUND, "No issue found with id '" + issueId + "'");
    }
}
