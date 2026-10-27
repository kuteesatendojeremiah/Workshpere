package com.worksphere.task;

import java.util.Map;
import java.util.UUID;

/** How many tasks a project has in each Kanban column. Cheap to read, so it is cached. */
public record TaskSummaryResponse(UUID projectId, Map<TaskStatus, Integer> countsByStatus, int total) {
}
