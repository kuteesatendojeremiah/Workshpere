package com.worksphere.file;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import org.springframework.http.HttpStatus;

public class StorageUnavailableException extends ApiException {

    public StorageUnavailableException(String message, Throwable cause) {
        super(ErrorCode.STORAGE_UNAVAILABLE, HttpStatus.SERVICE_UNAVAILABLE, message);
        initCause(cause);
    }
}
