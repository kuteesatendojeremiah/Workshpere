package com.worksphere.task;

import com.worksphere.identity.User;
import com.worksphere.project.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "tasks")
public class Task {

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
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TaskPriority priority;

    @ManyToOne
    @JoinColumn(name = "assignee_id")
    private User assignee;

    @Column(name = "due_date")
    private LocalDate dueDate;

    /** Position within its (project, status) Kanban column - the server's source of truth for order. */
    @Column(nullable = false)
    private int position;

    @Version
    @Column(nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Set when this task's status becomes DONE, cleared if it moves away from DONE - this
     * is what makes a real burndown chart possible, rather than a guess based on updatedAt
     * (which changes on any field edit, not just completion). */
    @Column(name = "completed_at")
    private Instant completedAt;

    protected Task() {
    }

    public Task(Project project, String title, String description, TaskPriority priority, LocalDate dueDate) {
        this.project = project;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.TODO;
        this.priority = priority != null ? priority : TaskPriority.MEDIUM;
        this.dueDate = dueDate;
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateDetails(String title, String description, TaskPriority priority, LocalDate dueDate) {
        this.title = title;
        this.description = description;
        this.priority = priority != null ? priority : TaskPriority.MEDIUM;
        this.dueDate = dueDate;
        this.updatedAt = Instant.now();
    }

    /** Moves this task into (or within) a column, at a specific position - the service is
     * responsible for re-sequencing every other affected task's position before/after calling this. */
    public void moveTo(TaskStatus status, int position) {
        if (status != this.status) {
            this.completedAt = status == TaskStatus.DONE ? Instant.now() : null;
        }
        this.status = status;
        this.position = position;
        this.updatedAt = Instant.now();
    }

    void setPosition(int position) {
        this.position = position;
    }

    public void assignTo(User assignee) {
        this.assignee = assignee;
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

    public TaskStatus getStatus() {
        return status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public User getAssignee() {
        return assignee;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public int getPosition() {
        return position;
    }

    public long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
