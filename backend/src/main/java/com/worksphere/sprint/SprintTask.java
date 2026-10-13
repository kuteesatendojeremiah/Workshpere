package com.worksphere.sprint;

import com.worksphere.task.Task;
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
@Table(name = "sprint_tasks")
public class SprintTask {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne
    @JoinColumn(name = "sprint_id", nullable = false)
    private Sprint sprint;

    @ManyToOne
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "story_points", nullable = false)
    private int storyPoints;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected SprintTask() {
    }

    public SprintTask(Sprint sprint, Task task, int storyPoints) {
        this.sprint = sprint;
        this.task = task;
        this.storyPoints = storyPoints;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public Sprint getSprint() {
        return sprint;
    }

    public Task getTask() {
        return task;
    }

    public int getStoryPoints() {
        return storyPoints;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
