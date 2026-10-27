import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import type { FileResponse } from '../api/types'
import { AttachmentsSection } from './AttachmentsSection'

// The global test setup replaces this component with a no-op; here we want the real one.
vi.unmock('../components/AttachmentsSection')
vi.mock('../api/filesApi', () => ({
  filesApi: { list: vi.fn(), upload: vi.fn(), download: vi.fn(), delete: vi.fn() },
}))

const { filesApi } = await import('../api/filesApi')

function file(overrides: Partial<FileResponse> = {}): FileResponse {
  return {
    id: 'f1',
    taskId: 't1',
    filename: 'logo.png',
    contentType: 'image/png',
    sizeBytes: 2048,
    uploadedBy: 'me',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function page(content: FileResponse[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 }
}

function renderSection() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <AttachmentsSection taskId="t1" />
    </QueryClientProvider>,
  )
}

describe('AttachmentsSection', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows a loading state, then the files with their sizes', async () => {
    vi.mocked(filesApi.list).mockResolvedValue(page([file()]))
    renderSection()

    expect(screen.getByText('Loading attachments…')).toBeInTheDocument()
    const item = await screen.findByRole('listitem')
    expect(item).toHaveTextContent('logo.png')
    expect(item).toHaveTextContent('(2.0 KB)')
  })

  it('shows an empty state when there are no files', async () => {
    vi.mocked(filesApi.list).mockResolvedValue(page([]))
    renderSection()

    expect(await screen.findByText('No attachments yet.')).toBeInTheDocument()
  })

  it('shows the backend message when the list cannot be loaded', async () => {
    vi.mocked(filesApi.list).mockRejectedValue(
      new ApiError(503, { code: 'STORAGE_UNAVAILABLE', message: 'File storage is not available right now', details: [], timestamp: '2026-01-01T00:00:00Z', path: '/x' }),
    )
    renderSection()

    expect(await screen.findByRole('alert')).toHaveTextContent('File storage is not available right now')
  })

  it('uploads the chosen file and refreshes the list', async () => {
    vi.mocked(filesApi.list).mockResolvedValueOnce(page([])).mockResolvedValue(page([file({ filename: 'notes.txt' })]))
    vi.mocked(filesApi.upload).mockResolvedValue(file({ filename: 'notes.txt' }))
    renderSection()
    await screen.findByText('No attachments yet.')

    const chosen = new File(['hello'], 'notes.txt', { type: 'text/plain' })
    await userEvent.upload(screen.getByLabelText(/Add a file/), chosen)

    await waitFor(() => expect(filesApi.upload).toHaveBeenCalledWith('t1', chosen))
    expect(await screen.findByRole('listitem')).toHaveTextContent('notes.txt')
  })

  it('shows the server reason when an upload is refused', async () => {
    vi.mocked(filesApi.list).mockResolvedValue(page([]))
    vi.mocked(filesApi.upload).mockRejectedValue(
      new ApiError(415, { code: 'INVALID_FILE', message: 'This type of file is not allowed', details: [], timestamp: '2026-01-01T00:00:00Z', path: '/x' }),
    )
    renderSection()
    await screen.findByText('No attachments yet.')

    await userEvent.upload(screen.getByLabelText(/Add a file/), new File(['MZ'], 'setup.png', { type: 'image/png' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('This type of file is not allowed')
  })

  it('refuses a file over 10 MB without contacting the server', async () => {
    vi.mocked(filesApi.list).mockResolvedValue(page([]))
    renderSection()
    await screen.findByText('No attachments yet.')

    const huge = new File([new Uint8Array(10 * 1024 * 1024 + 1)], 'huge.txt', { type: 'text/plain' })
    await userEvent.upload(screen.getByLabelText(/Add a file/), huge)

    expect(await screen.findByRole('alert')).toHaveTextContent('larger than the 10 MB limit')
    expect(filesApi.upload).not.toHaveBeenCalled()
  })

  it('deletes a file and refreshes the list', async () => {
    vi.mocked(filesApi.list).mockResolvedValueOnce(page([file()])).mockResolvedValue(page([]))
    vi.mocked(filesApi.delete).mockResolvedValue(undefined)
    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: 'Delete logo.png' }))

    await waitFor(() => expect(filesApi.delete).toHaveBeenCalledWith('f1'))
    expect(await screen.findByText('No attachments yet.')).toBeInTheDocument()
  })

  it('downloads a file by fetching it with the session and saving it', async () => {
    vi.mocked(filesApi.list).mockResolvedValue(page([file()]))
    vi.mocked(filesApi.download).mockResolvedValue(new Blob(['png-bytes']))
    const createUrl = vi.fn(() => 'blob:fake')
    const revokeUrl = vi.fn()
    Object.assign(URL, { createObjectURL: createUrl, revokeObjectURL: revokeUrl })
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    renderSection()

    await userEvent.click(await screen.findByRole('button', { name: 'Download logo.png' }))

    await waitFor(() => expect(click).toHaveBeenCalled())
    expect(filesApi.download).toHaveBeenCalledWith('f1')
    expect(revokeUrl).toHaveBeenCalledWith('blob:fake')
    click.mockRestore()
  })
})
