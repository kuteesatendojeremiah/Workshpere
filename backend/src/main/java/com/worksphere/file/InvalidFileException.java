package com.worksphere.file;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

/** The upload was refused: empty, too large, a type we do not accept, or a type that does not match its name. */
public class InvalidFileException extends ApiException {

    public InvalidFileException(HttpStatus status, String message) {
        super(ErrorCode.INVALID_FILE, status, message);
    }
}
