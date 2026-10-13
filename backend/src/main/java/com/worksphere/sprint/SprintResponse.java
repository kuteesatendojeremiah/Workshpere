package com.worksphere.sprint;

import java.time.LocalDate;
import java.util.UUID;

public record SprintResponse(
        UUID id, UUID projectId, String name, String goal, SprintStatus status, LocalDate startDate, LocalDate endDate) {

    public static SprintResponse from(Sprint sprint) {
        return new SprintResponse(
                sprint.getId(),
                sprint.getProject().getId(),
                sprint.getName(),
                sprint.getGoal(),
                sprint.getStatus(),
                sprint.getStartDate(),
                sprint.getEndDate());
    }
}
