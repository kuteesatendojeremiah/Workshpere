package com.worksphere.sprint;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

public class SprintNotFoundException extends ApiException {

    public SprintNotFoundException(UUID sprintId) {
        super(ErrorCode.SPRINT_NOT_FOUND, HttpStatus.NOT_FOUND, "No sprint found with id '" + sprintId + "'");
    }
}
