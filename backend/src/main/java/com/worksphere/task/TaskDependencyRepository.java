package com.worksphere.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskDependencyRepository extends JpaRepository<TaskDependency, UUID> {

    Optional<TaskDependency> findByTaskIdAndDependsOnTaskId(UUID taskId, UUID dependsOnTaskId);

    List<TaskDependency> findAllByTaskId(UUID taskId);

    void deleteByTaskIdAndDependsOnTaskId(UUID taskId, UUID dependsOnTaskId);
}
