package com.worksphere.task;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class LabelNotFoundException extends ApiException {

    public LabelNotFoundException(UUID labelId) {
        super(ErrorCode.LABEL_NOT_FOUND, HttpStatus.NOT_FOUND, "No label found with id '" + labelId + "'");
    }
}
