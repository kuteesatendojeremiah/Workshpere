import { act, render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { RealtimeEvent } from '../api/realtime'
import { NotificationBadge } from './NotificationBadge'

// The global test setup replaces the badge with a no-op; here we want the real one.
vi.unmock('../components/NotificationBadge')
vi.mock('../api/notificationsApi', () => ({ notificationsApi: { unreadCount: vi.fn() } }))

const { notificationsApi } = await import('../api/notificationsApi')
const realtime = await import('../api/realtime')

function renderBadge() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <NotificationBadge />
    </QueryClientProvider>,
  )
}

describe('NotificationBadge', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows nothing when there are no unread notifications', async () => {
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue(0)
    renderBadge()

    await vi.waitFor(() => expect(notificationsApi.unreadCount).toHaveBeenCalled())
    expect(screen.queryByRole('status')).not.toBeInTheDocument()
  })

  it('shows the unread count, and refreshes it when the server pushes a notification', async () => {
    vi.mocked(notificationsApi.unreadCount).mockResolvedValueOnce(2).mockResolvedValue(3)
    let push: (event: RealtimeEvent) => void = () => undefined
    vi.mocked(realtime.subscribeToTopic).mockImplementation((_destination, onEvent) => {
      push = onEvent
      return () => undefined
    })

    renderBadge()
    expect(await screen.findByRole('status', { name: '2 unread notifications' })).toBeInTheDocument()
    expect(realtime.subscribeToTopic).toHaveBeenCalledWith('/user/queue/notifications', expect.any(Function))

    act(() => push({ type: 'NOTIFICATION_CREATED' }))

    expect(await screen.findByRole('status', { name: '3 unread notifications' })).toBeInTheDocument()
  })
})
