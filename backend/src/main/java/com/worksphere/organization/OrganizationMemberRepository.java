package com.worksphere.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    Page<OrganizationMember> findAllByOrganizationId(UUID organizationId, Pageable pageable);

    int countByOrganizationId(UUID organizationId);

    Page<OrganizationMember> findAllByUserId(UUID userId, Pageable pageable);
}
