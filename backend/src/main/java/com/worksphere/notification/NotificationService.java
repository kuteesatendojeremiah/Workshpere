package com.worksphere.notification;

import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one place every module calls to notify a user - like {@code common.audit.AuditService},
 * this takes plain ids so callers (task, issue, comment) never need to hand over entities, and
 * this module never needs to depend on any of theirs.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public void notify(UUID recipientId, NotificationType type, String message, String resourceType, UUID resourceId) {
        notificationRepository.save(new Notification(recipientId, type, message, resourceType, resourceId));
    }

    @Transactional(readOnly = true)
    public Page<Notification> listNotifications(UUID recipientId, boolean unreadOnly, Pageable pageable) {
        return unreadOnly
                ? notificationRepository.findAllByRecipientIdAndReadAtIsNull(recipientId, pageable)
                : notificationRepository.findAllByRecipientId(recipientId, pageable);
    }

    @Transactional
    public Notification markRead(UUID notificationId, UUID recipientId) {
        Notification notification = notificationRepository
                .findByIdAndRecipientId(notificationId, recipientId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        notification.markRead();
        return notification;
    }

    @Transactional
    public void markAllRead(UUID recipientId) {
        notificationRepository.findAllByRecipientIdAndReadAtIsNull(recipientId).forEach(Notification::markRead);
    }
}
