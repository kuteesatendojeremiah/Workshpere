import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { ProjectOverviewPage } from './ProjectOverviewPage'
import { ApiError } from '../api/client'

vi.mock('../api/projectsApi', () => ({
  projectsApi: { get: vi.fn(), listMembers: vi.fn(), update: vi.fn(), archive: vi.fn(), addMember: vi.fn(), removeMember: vi.fn() },
}))
vi.mock('../api/organizationsApi', () => ({
  organizationsApi: { listMembers: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { id: 'me', displayName: 'Me' }, logout: vi.fn() }),
}))

const { projectsApi } = await import('../api/projectsApi')
const { organizationsApi } = await import('../api/organizationsApi')

const SAMPLE_PROJECT = {
  id: 'proj-1',
  organizationId: 'org-1',
  name: 'Website',
  description: 'The marketing site',
  status: 'ACTIVE' as const,
  createdAt: '2026-01-01T00:00:00Z',
}

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/organizations/org-1/projects/proj-1']}>
        <Routes>
          <Route path="/organizations/:organizationId/projects/:projectId" element={<ProjectOverviewPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('ProjectOverviewPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(projectsApi.get).mockResolvedValue(SAMPLE_PROJECT)
    vi.mocked(projectsApi.listMembers).mockResolvedValue({
      content: [{ userId: 'me', email: 'me@example.com', displayName: 'Me', joinedAt: '2026-01-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
  })

  it('shows project details and members for a regular member, with no management controls', async () => {
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [{ userId: 'me', email: 'me@example.com', displayName: 'Me', role: 'EMPLOYEE_MEMBER', joinedAt: '2026-01-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })

    renderPage()

    expect(await screen.findByRole('heading', { name: /Website/ })).toBeInTheDocument()
    expect(screen.getByText('The marketing site')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Archive' })).not.toBeInTheDocument()
  })

  it('lets an owner edit the project', async () => {
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [{ userId: 'me', email: 'me@example.com', displayName: 'Me', role: 'ORGANIZATION_OWNER', joinedAt: '2026-01-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(projectsApi.update).mockResolvedValue({ ...SAMPLE_PROJECT, name: 'Website v2' })

    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    const nameInput = screen.getByLabelText('Project name')
    await user.clear(nameInput)
    await user.type(nameInput, 'Website v2')
    await user.click(screen.getByRole('button', { name: 'Save' }))

    await waitFor(() =>
      expect(projectsApi.update).toHaveBeenCalledWith('org-1', 'proj-1', 'Website v2', 'The marketing site'),
    )
  })

  it('lets an owner archive the project', async () => {
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [{ userId: 'me', email: 'me@example.com', displayName: 'Me', role: 'ORGANIZATION_OWNER', joinedAt: '2026-01-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(projectsApi.archive).mockResolvedValue({ ...SAMPLE_PROJECT, status: 'ARCHIVED' })

    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: 'Archive' }))

    await waitFor(() => expect(projectsApi.archive).toHaveBeenCalledWith('org-1', 'proj-1'))
  })

  it('lets an owner add an organization member not already on the project', async () => {
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [
        { userId: 'me', email: 'me@example.com', displayName: 'Me', role: 'ORGANIZATION_OWNER', joinedAt: '2026-01-01T00:00:00Z' },
        { userId: 'other', email: 'other@example.com', displayName: 'Other', role: 'EMPLOYEE_MEMBER', joinedAt: '2026-01-01T00:00:00Z' },
      ],
      page: 0,
      size: 50,
      totalElements: 2,
      totalPages: 1,
    })
    vi.mocked(projectsApi.addMember).mockResolvedValue({
      userId: 'other',
      email: 'other@example.com',
      displayName: 'Other',
      joinedAt: '2026-01-01T00:00:00Z',
    })

    renderPage()
    const user = userEvent.setup()

    const select = await screen.findByLabelText('Add a member from this organization')
    await user.selectOptions(select, 'other')

    await waitFor(() => expect(projectsApi.addMember).toHaveBeenCalledWith('org-1', 'proj-1', 'other'))
  })

  it('shows a not-found message when the project cannot be loaded', async () => {
    vi.mocked(projectsApi.get).mockRejectedValue(
      new ApiError(404, {
        code: 'PROJECT_NOT_FOUND',
        message: "No project found with id 'proj-1'",
        timestamp: '2026-01-01T00:00:00Z',
        path: '/api/v1/organizations/org-1/projects/proj-1',
        details: [],
      }),
    )

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent("No project found with id 'proj-1'")
  })
})
