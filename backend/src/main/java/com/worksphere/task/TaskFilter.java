package com.worksphere.task;

import java.util.UUID;

public record TaskFilter(TaskStatus status, TaskPriority priority, UUID assigneeId) {
}
