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
@Table(name = "task_labels")
public class TaskLabel {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @ManyToOne
    @JoinColumn(name = "label_id", nullable = false)
    private Label label;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected TaskLabel() {
    }

    public TaskLabel(Task task, Label label) {
        this.task = task;
        this.label = label;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Task getTask() {
        return task;
    }

    public Label getLabel() {
        return label;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
