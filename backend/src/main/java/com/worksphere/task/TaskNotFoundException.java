package com.worksphere.task;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a task doesn't exist OR the caller has no access to its project - deliberately
 * indistinguishable, same reasoning as {@link com.worksphere.project.ProjectNotFoundException}.
 */
public class TaskNotFoundException extends ApiException {

    public TaskNotFoundException(UUID taskId) {
        super(ErrorCode.TASK_NOT_FOUND, HttpStatus.NOT_FOUND, "No task found with id '" + taskId + "'");
    }
}
