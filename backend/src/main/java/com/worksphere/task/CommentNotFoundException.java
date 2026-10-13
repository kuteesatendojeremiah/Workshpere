package com.worksphere.task;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class CommentNotFoundException extends ApiException {

    public CommentNotFoundException(UUID commentId) {
        super(ErrorCode.COMMENT_NOT_FOUND, HttpStatus.NOT_FOUND, "No comment found with id '" + commentId + "'");
    }
}
