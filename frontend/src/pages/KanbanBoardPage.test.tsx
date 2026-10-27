import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { KanbanBoardPage } from './KanbanBoardPage'
import type { TaskResponse, TaskStatus } from '../api/types'

vi.mock('../api/tasksApi', () => ({
  tasksApi: { listByStatus: vi.fn(), create: vi.fn(), changeStatus: vi.fn() },
}))
vi.mock('../api/organizationsApi', () => ({
  organizationsApi: { listMembers: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'me', displayName: 'Me' }, logout: vi.fn() }),
}))

const { tasksApi } = await import('../api/tasksApi')
const { organizationsApi } = await import('../api/organizationsApi')

function page<T>(content: T[]) {
  return { content, page: 0, size: 200, totalElements: content.length, totalPages: 1 }
}

function task(overrides: Partial<TaskResponse>): TaskResponse {
  return {
    id: 'task-1',
    projectId: 'proj-1',
    title: 'Do the thing',
    description: null,
    status: 'TODO',
    position: 0,
    priority: 'MEDIUM',
    assigneeId: null,
    dueDate: null,
    version: 0,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  }
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/organizations/org-1/projects/proj-1/board']}>
        <Routes>
          <Route path="/organizations/:organizationId/projects/:projectId/board" element={<KanbanBoardPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('KanbanBoardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(organizationsApi.listMembers).mockResolvedValue(
      page([{ userId: 'me', email: 'me@example.com', displayName: 'Me', role: 'ORGANIZATION_OWNER', joinedAt: '2026-01-01T00:00:00Z' }]),
    )
    vi.mocked(tasksApi.listByStatus).mockImplementation((_projectId: string, status: TaskStatus) =>
      Promise.resolve(page(status === 'TODO' ? [task({ id: 't1', title: 'First task' })] : [])),
    )
  })

  it('renders each status column with its tasks', async () => {
    renderPage()

    expect(await screen.findByText('First task')).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'To Do' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'In Progress' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'In Review' })).toBeInTheDocument()
    expect(screen.getByRole('region', { name: 'Done' })).toBeInTheDocument()
  })

  it('shows a loading state while the columns load', async () => {
    vi.mocked(tasksApi.listByStatus).mockReturnValue(new Promise(() => undefined))

    renderPage()

    expect(await screen.findByText('Loading board…')).toBeInTheDocument()
  })

  it('shows the backend error message when the board cannot be loaded', async () => {
    vi.mocked(tasksApi.listByStatus).mockRejectedValue(
      new ApiError(403, { code: 'MISSING_PERMISSION', message: 'You cannot view this project', details: [], timestamp: '2026-01-01T00:00:00Z', path: '/api/v1/test' }),
    )

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('You cannot view this project')
  })

  it('renders empty columns when the project has no tasks', async () => {
    vi.mocked(tasksApi.listByStatus).mockResolvedValue(page([]))

    renderPage()

    expect(await screen.findByRole('region', { name: 'To Do' })).toBeInTheDocument()
    expect(screen.queryByText('First task')).not.toBeInTheDocument()
    expect(screen.queryByText('Loading board…')).not.toBeInTheDocument()
  })

  it('lets a manager create a new task, landing it in To Do', async () => {
    vi.mocked(tasksApi.create).mockResolvedValue(task({ id: 't2', title: 'New task' }))

    renderPage()
    const user = userEvent.setup()

    await user.type(await screen.findByLabelText('New task'), 'New task')
    await user.click(screen.getByRole('button', { name: 'Add task' }))

    await waitFor(() => expect(tasksApi.create).toHaveBeenCalledWith('proj-1', 'New task', 'MEDIUM'))
  })

  it('hides the create-task form from a regular member', async () => {
    vi.mocked(organizationsApi.listMembers).mockResolvedValue(
      page([{ userId: 'me', email: 'me@example.com', displayName: 'Me', role: 'EMPLOYEE_MEMBER', joinedAt: '2026-01-01T00:00:00Z' }]),
    )

    renderPage()

    await screen.findByText('First task')
    expect(screen.queryByLabelText('New task')).not.toBeInTheDocument()
  })
})
