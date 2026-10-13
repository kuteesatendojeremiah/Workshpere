package com.worksphere.notification;

import com.worksphere.common.error.ApiException;
import com.worksphere.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Thrown both when a notification doesn't exist and when it belongs to someone else - a
 * notification is never visible to anyone but its recipient, so the two cases are indistinguishable. */
public class NotificationNotFoundException extends ApiException {

    public NotificationNotFoundException(UUID notificationId) {
        super(
                ErrorCode.NOTIFICATION_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "No notification found with id '" + notificationId + "'");
    }
}
