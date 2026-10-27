import { apiClient } from './client'
import type { PageResponse } from './types'

// Only the fields the UI needs; the backend sends more (see NotificationResponse).
export interface NotificationSummary {
  id: string
  message: string
  readAt: string | null
}

export const notificationsApi = {
  // size=1 because we only want the total count of unread notifications, not the notifications.
  unreadCount: async () => {
    const page = await apiClient.get<PageResponse<NotificationSummary>>('/api/v1/notifications?unreadOnly=true&size=1')
    return page.totalElements
  },
}
