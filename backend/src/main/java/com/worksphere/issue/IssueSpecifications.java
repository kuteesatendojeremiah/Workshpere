package com.worksphere.issue;

import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;

final class IssueSpecifications {

    private IssueSpecifications() {
    }

    static Specification<Issue> inProject(UUID projectId) {
        return (root, query, cb) -> cb.equal(root.get("project").get("id"), projectId);
    }

    static Specification<Issue> hasSeverity(IssueSeverity severity) {
        return (root, query, cb) -> severity == null ? null : cb.equal(root.get("severity"), severity);
    }

    static Specification<Issue> hasPriority(IssuePriority priority) {
        return (root, query, cb) -> priority == null ? null : cb.equal(root.get("priority"), priority);
    }

    static Specification<Issue> hasEnvironment(IssueEnvironment environment) {
        return (root, query, cb) -> environment == null ? null : cb.equal(root.get("environment"), environment);
    }

    static Specification<Issue> hasStatus(IssueStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}
