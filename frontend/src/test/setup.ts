import '@testing-library/jest-dom/vitest'

// Components that open a WebSocket are replaced by no-ops in tests; the real WebSocket path is
// covered by the backend integration test and by the dedicated realtime tests (which unmock).
import { vi } from 'vitest'
vi.mock('../api/realtime', () => ({ subscribeToTopic: vi.fn(() => () => undefined) }))
vi.mock('../components/NotificationBadge', () => ({ NotificationBadge: () => null }))
vi.mock('../components/AttachmentsSection', () => ({ AttachmentsSection: () => null }))
