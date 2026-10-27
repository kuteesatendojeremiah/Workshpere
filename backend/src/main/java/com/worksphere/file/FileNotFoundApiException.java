package com.worksphere.file;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class FileNotFoundApiException extends ApiException {

    public FileNotFoundApiException(UUID fileId) {
        super(ErrorCode.FILE_NOT_FOUND, HttpStatus.NOT_FOUND, "File not found: " + fileId);
    }
}
