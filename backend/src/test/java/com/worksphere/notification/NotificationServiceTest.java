package com.worksphere.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(notificationRepository);
    }

    @Test
    void notifySavesANotificationForTheRecipient() {
        UUID recipientId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        notificationService.notify(recipientId, NotificationType.TASK_ASSIGNED, "You were assigned", "TASK", resourceId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        org.mockito.Mockito.verify(notificationRepository).save(captor.capture());
        assertThat(captor.getValue().getRecipientId()).isEqualTo(recipientId);
        assertThat(captor.getValue().isRead()).isFalse();
    }

    @Test
    void markReadRejectsSomeoneElsesNotification() {
        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markRead(notificationId, recipientId))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    void markReadSetsReadAt() {
        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Notification notification =
                new Notification(recipientId, NotificationType.MENTIONED, "Hi", "TASK", UUID.randomUUID());
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.of(notification));

        Notification result = notificationService.markRead(notificationId, recipientId);

        assertThat(result.isRead()).isTrue();
    }

    @Test
    void markAllReadMarksEveryUnreadNotification() {
        UUID recipientId = UUID.randomUUID();
        Notification first =
                new Notification(recipientId, NotificationType.TASK_ASSIGNED, "A", "TASK", UUID.randomUUID());
        Notification second =
                new Notification(recipientId, NotificationType.MENTIONED, "B", "TASK", UUID.randomUUID());
        when(notificationRepository.findAllByRecipientIdAndReadAtIsNull(recipientId))
                .thenReturn(List.of(first, second));

        notificationService.markAllRead(recipientId);

        assertThat(first.isRead()).isTrue();
        assertThat(second.isRead()).isTrue();
    }
}
