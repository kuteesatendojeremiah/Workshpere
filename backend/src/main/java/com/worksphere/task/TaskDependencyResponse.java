package com.worksphere.task;

import java.util.UUID;

public record TaskDependencyResponse(UUID taskId, UUID dependsOnTaskId) {

    public static TaskDependencyResponse from(TaskDependency dependency) {
        return new TaskDependencyResponse(dependency.getTask().getId(), dependency.getDependsOnTask().getId());
    }
}
