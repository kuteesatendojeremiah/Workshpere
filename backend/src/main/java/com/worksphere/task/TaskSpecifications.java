package com.worksphere.task;

import java.util.Collection;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class TaskSpecifications {

    private TaskSpecifications() {
    }

    static Specification<Task> inProject(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    static Specification<Task> hasStatus(TaskStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }

    static Specification<Task> hasPriority(TaskPriority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    static Specification<Task> hasAssignee(UUID assigneeId) {
        return (root, query, cb) ->
                assigneeId == null ? null : cb.equal(root.get("assignee").get("id"), assigneeId);
    }

    static Specification<Task> idNotIn(Collection<UUID> excludedIds) {
        return (root, query, cb) ->
                excludedIds == null || excludedIds.isEmpty() ? null : cb.not(root.get("id").in(excludedIds));
    }
}
