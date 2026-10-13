package com.worksphere.team;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    Page<TeamMember> findAllByTeamId(UUID teamId, Pageable pageable);

    void deleteByTeamIdAndUserId(UUID teamId, UUID userId);
}
