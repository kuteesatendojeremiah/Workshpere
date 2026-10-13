package com.worksphere.task;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "task_dependencies")
public class TaskDependency {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne
    @JoinColumn(name = "depends_on_task_id", nullable = false)
    private Task dependsOnTask;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskDependency() {
    }

    public TaskDependency(Task task, Task dependsOnTask) {
        this.task = task;
        this.dependsOnTask = dependsOnTask;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public Task getDependsOnTask() {
        return dependsOnTask;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
