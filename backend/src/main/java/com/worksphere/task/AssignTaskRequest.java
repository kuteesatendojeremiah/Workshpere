package com.worksphere.task;

import java.util.UUID;

public record AssignTaskRequest(UUID assigneeId, long version) {
}
