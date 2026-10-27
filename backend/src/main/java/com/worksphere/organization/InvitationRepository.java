package com.worksphere.organization;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvitationRepository extends JpaRepository<Invitation, UUID> {

    Optional<Invitation> findByTokenHash(String tokenHash);

    /** Invitations that could still turn into a member: not accepted and not expired. */
    int countByOrganizationIdAndAcceptedAtIsNullAndExpiresAtAfter(UUID organizationId, java.time.Instant now);
}
