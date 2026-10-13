package com.worksphere.notification;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByRecipientId(UUID recipientId, Pageable pageable);

    Page<Notification> findAllByRecipientIdAndReadAtIsNull(UUID recipientId, Pageable pageable);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    List<Notification> findAllByRecipientIdAndReadAtIsNull(UUID recipientId);

    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
