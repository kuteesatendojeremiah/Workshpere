package com.worksphere.project;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByProjectIdAndUserId(UUID projectId, UUID userId);

    Page<ProjectMember> findAllByProjectId(UUID projectId, Pageable pageable);

    Page<ProjectMember> findAllByProject_Organization_IdAndUser_Id(
            UUID organizationId, UUID userId, Pageable pageable);

    void deleteByProjectIdAndUserId(UUID projectId, UUID userId);
}
