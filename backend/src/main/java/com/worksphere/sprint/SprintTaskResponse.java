package com.worksphere.sprint;

import java.util.UUID;

public record SprintTaskResponse(UUID taskId, String taskTitle, int storyPoints, boolean completed) {

    public static SprintTaskResponse from(SprintTask sprintTask) {
        return new SprintTaskResponse(
                sprintTask.getTask().getId(),
                sprintTask.getTask().getTitle(),
                sprintTask.getStoryPoints(),
                sprintTask.getTask().getCompletedAt() != null);
    }
}
