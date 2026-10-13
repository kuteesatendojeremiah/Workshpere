package com.worksphere.sprint;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintTaskRepository extends JpaRepository<SprintTask, UUID> {

    Optional<SprintTask> findBySprintIdAndTaskId(UUID sprintId, UUID taskId);

    List<SprintTask> findAllBySprintId(UUID sprintId);

    void deleteBySprintIdAndTaskId(UUID sprintId, UUID taskId);

    List<SprintTask> findAllBySprint_Project_IdAndSprint_StatusIn(UUID projectId, List<SprintStatus> statuses);
}
