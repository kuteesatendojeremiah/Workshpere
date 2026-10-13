package com.worksphere.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface TaskRepository extends JpaRepository<Task, UUID>, JpaSpecificationExecutor<Task> {

    Optional<Task> findByIdAndProjectId(UUID id, UUID projectId);

    List<Task> findAllByProject_IdAndStatusOrderByPositionAsc(UUID projectId, TaskStatus status);

    int countByProject_IdAndStatus(UUID projectId, TaskStatus status);
}
