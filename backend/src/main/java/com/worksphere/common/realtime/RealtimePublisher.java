package com.worksphere.common.realtime;

import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * The one place other modules call to push a live event to browsers, like AuditService and
 * NotificationService. Two deliberate rules:
 *
 * <ul>
 *   <li>Events are sent only AFTER the database transaction commits. Otherwise a rolled-back
 *       change would still show up on someone's screen.
 *   <li>Payloads carry ids only, never data. The browser re-fetches over REST, where the normal
 *       authorization and tenant checks apply. A WebSocket message can never leak more than
 *       "something changed here".
 * </ul>
 *
 * A failed push is logged and swallowed: real-time is a nicety, REST is the source of truth.
 */
@Component
public class RealtimePublisher {

    private static final Logger log = LoggerFactory.getLogger(RealtimePublisher.class);

    private final SimpMessagingTemplate messagingTemplate;

    public RealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /** Delivered to {@code /user/queue/notifications} for exactly one user. */
    public void notificationCreated(UUID recipientId, UUID notificationId) {
        afterCommit(() -> messagingTemplate.convertAndSendToUser(
                recipientId.toString(),
                "/queue/notifications",
                Map.of("type", "NOTIFICATION_CREATED", "notificationId", notificationId.toString())));
    }

    /** Delivered to everyone subscribed to {@code /topic/projects/{projectId}/board}. */
    public void boardChanged(UUID projectId, String changeType, UUID taskId) {
        afterCommit(() -> messagingTemplate.convertAndSend(
                "/topic/projects/" + projectId + "/board",
                Map.of("type", changeType, "projectId", projectId.toString(), "taskId", taskId.toString())));
    }

    private void afterCommit(Runnable send) {
        Runnable safeSend = () -> {
            try {
                send.run();
            } catch (RuntimeException e) {
                log.warn("Could not push real-time event: {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    safeSend.run();
                }
            });
        } else {
            safeSend.run();
        }
    }
}
