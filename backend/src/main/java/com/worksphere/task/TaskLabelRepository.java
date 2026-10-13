package com.worksphere.task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaskLabelRepository extends JpaRepository<TaskLabel, UUID> {

    List<TaskLabel> findAllByTaskId(UUID taskId);

    Optional<TaskLabel> findByTaskIdAndLabelId(UUID taskId, UUID labelId);

    void deleteByTaskIdAndLabelId(UUID taskId, UUID labelId);
}
