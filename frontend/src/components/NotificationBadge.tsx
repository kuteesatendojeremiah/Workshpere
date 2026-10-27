import { useQuery, useQueryClient } from '@tanstack/react-query'
import { notificationsApi } from '../api/notificationsApi'
import { useRealtimeTopic } from '../api/useRealtimeTopic'

const UNREAD_KEY = ['notifications', 'unread-count']

/** Unread-notification count in the header; updates live when the server pushes a new one. */
export function NotificationBadge() {
  const queryClient = useQueryClient()
  const { data: count } = useQuery({ queryKey: UNREAD_KEY, queryFn: notificationsApi.unreadCount })

  useRealtimeTopic('/user/queue/notifications', () => {
    void queryClient.invalidateQueries({ queryKey: UNREAD_KEY })
  })

  if (!count) {
    return null
  }
  return (
    <span className="app-header__notifications" role="status" aria-label={`${count} unread notifications`}>
      {count}
    </span>
  )
}
