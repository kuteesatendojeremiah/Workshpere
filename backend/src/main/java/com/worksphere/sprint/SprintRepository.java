package com.worksphere.sprint;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SprintRepository extends JpaRepository<Sprint, UUID> {

    Optional<Sprint> findByIdAndProjectId(UUID id, UUID projectId);

    Page<Sprint> findAllByProjectId(UUID projectId, Pageable pageable);

    boolean existsByProject_IdAndStatus(UUID projectId, SprintStatus status);
}
