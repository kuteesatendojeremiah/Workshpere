import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '../api/client'
import { ProjectsListPage } from './ProjectsListPage'

vi.mock('../api/projectsApi', () => ({
  projectsApi: { list: vi.fn(), create: vi.fn() },
}))
vi.mock('../api/organizationsApi', () => ({
  organizationsApi: { listMembers: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'me', displayName: 'Me' }, logout: vi.fn() }),
}))

const { projectsApi } = await import('../api/projectsApi')
const { organizationsApi } = await import('../api/organizationsApi')

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/organizations/org-1/projects']}>
        <Routes>
          <Route path="/organizations/:organizationId/projects" element={<ProjectsListPage />} />
          <Route path="/organizations/:organizationId/projects/:projectId" element={<div>Overview screen</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ProjectsListPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [{ userId: 'me', email: 'me@example.com', displayName: 'Me', role: 'ORGANIZATION_OWNER', joinedAt: '2026-01-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
  })

  it('shows a loading state while projects load', async () => {
    vi.mocked(projectsApi.list).mockReturnValue(new Promise(() => undefined))

    renderPage()

    expect(await screen.findByText('Loading projects…')).toBeInTheDocument()
  })

  it('shows the backend error message when projects cannot be loaded', async () => {
    vi.mocked(projectsApi.list).mockRejectedValue(
      new ApiError(403, { code: 'NOT_A_MEMBER', message: 'You are not a member of this organization', details: [], timestamp: '2026-01-01T00:00:00Z', path: '/api/v1/test' }),
    )

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent('You are not a member of this organization')
  })

  it('shows an empty state with no projects', async () => {
    vi.mocked(projectsApi.list).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })

    renderPage()

    expect(await screen.findByText(/No projects yet/)).toBeInTheDocument()
  })

  it('shows the owner the create-project control and creates one', async () => {
    vi.mocked(projectsApi.list).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })
    vi.mocked(projectsApi.create).mockResolvedValue({
      id: 'proj-1',
      organizationId: 'org-1',
      name: 'Website',
      description: null,
      status: 'ACTIVE',
      createdAt: '2026-01-01T00:00:00Z',
    })

    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: '+ New project' }))
    await user.type(screen.getByLabelText('Project name'), 'Website')
    await user.click(screen.getByRole('button', { name: 'Create' }))

    await waitFor(() => expect(projectsApi.create).toHaveBeenCalledWith('org-1', 'Website', undefined))
  })

  it('lists projects and links to their overview page', async () => {
    vi.mocked(projectsApi.list).mockResolvedValue({
      content: [
        {
          id: 'proj-1',
          organizationId: 'org-1',
          name: 'Website',
          description: null,
          status: 'ACTIVE',
          createdAt: '2026-01-01T00:00:00Z',
        },
      ],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })

    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('link', { name: 'Website' }))

    expect(await screen.findByText('Overview screen')).toBeInTheDocument()
  })
})
