package com.worksphere.notification;

import com.worksphere.common.pagination.PageResponse;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/api/v1/notifications")
    public PageResponse<NotificationResponse> list(
            @AuthenticationPrincipal UUID callerUserId,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            Pageable pageable) {
        return PageResponse.from(notificationService
                .listNotifications(callerUserId, unreadOnly, pageable)
                .map(NotificationResponse::from));
    }

    @PostMapping("/api/v1/notifications/{notificationId}/read")
    public NotificationResponse markRead(
            @PathVariable UUID notificationId, @AuthenticationPrincipal UUID callerUserId) {
        return NotificationResponse.from(notificationService.markRead(notificationId, callerUserId));
    }

    @PostMapping("/api/v1/notifications/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markAllRead(@AuthenticationPrincipal UUID callerUserId) {
        notificationService.markAllRead(callerUserId);
    }
}
