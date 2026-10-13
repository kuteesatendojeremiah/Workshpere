package com.worksphere.issue;

import com.worksphere.identity.User;
import com.worksphere.project.Project;
import com.worksphere.task.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "issues")
public class Issue {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String title;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssuePriority priority;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueEnvironment environment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IssueStatus status;

    @ManyToOne
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @ManyToOne
    @JoinColumn(name = "linked_task_id")
    private Task linkedTask;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Issue() {
    }

    public Issue(
            Project project,
            String title,
            String description,
            IssueSeverity severity,
            IssuePriority priority,
            IssueEnvironment environment,
            User reporter) {
        this.project = project;
        this.title = title;
        this.description = description;
        this.severity = severity != null ? severity : IssueSeverity.MEDIUM;
        this.priority = priority != null ? priority : IssuePriority.MEDIUM;
        this.environment = environment != null ? environment : IssueEnvironment.PRODUCTION;
        this.status = IssueStatus.OPEN;
        this.reporter = reporter;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(
            String title, String description, IssueSeverity severity, IssuePriority priority, IssueEnvironment environment) {
        this.title = title;
        this.description = description;
        this.severity = severity != null ? severity : IssueSeverity.MEDIUM;
        this.priority = priority != null ? priority : IssuePriority.MEDIUM;
        this.environment = environment != null ? environment : IssueEnvironment.PRODUCTION;
        this.updatedAt = Instant.now();
    }

    public void changeStatus(IssueStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public void assignTo(User assignee) {
        this.assignee = assignee;
        this.updatedAt = Instant.now();
    }

    public void linkToTask(Task task) {
        this.linkedTask = task;
        this.updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public IssueSeverity getSeverity() {
        return severity;
    }

    public IssuePriority getPriority() {
        return priority;
    }

    public IssueEnvironment getEnvironment() {
        return environment;
    }

    public IssueStatus getStatus() {
        return status;
    }

    public User getReporter() {
        return reporter;
    }

    public User getAssignee() {
        return assignee;
    }

    public Task getLinkedTask() {
        return linkedTask;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
