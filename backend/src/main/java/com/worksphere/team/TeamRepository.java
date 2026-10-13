package com.worksphere.team;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TeamRepository extends JpaRepository<Team, UUID> {

    Optional<Team> findByIdAndOrganizationId(UUID id, UUID organizationId);

    Page<Team> findAllByOrganizationId(UUID organizationId, Pageable pageable);
}
