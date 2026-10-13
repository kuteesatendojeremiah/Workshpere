import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { DashboardPage } from './DashboardPage'
import { ApiError } from '../api/client'

vi.mock('../api/organizationsApi', () => ({
  organizationsApi: { get: vi.fn(), listMembers: vi.fn(), listMine: vi.fn(), create: vi.fn() },
}))
vi.mock('../api/projectsApi', () => ({
  projectsApi: { list: vi.fn(), create: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { displayName: 'Me' }, logout: vi.fn() }),
}))

const { organizationsApi } = await import('../api/organizationsApi')
const { projectsApi } = await import('../api/projectsApi')

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/organizations/org-1']}>
        <Routes>
          <Route path="/organizations/:organizationId" element={<DashboardPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('DashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows the organization name, stats, and projects once loaded', async () => {
    vi.mocked(organizationsApi.get).mockResolvedValue({
      id: 'org-1',
      name: 'Acme Inc',
      createdAt: '2026-01-01T00:00:00Z',
    })
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [],
      page: 0,
      size: 50,
      totalElements: 3,
      totalPages: 1,
    })
    vi.mocked(projectsApi.list).mockResolvedValue({
      content: [{ id: 'proj-1', organizationId: 'org-1', name: 'Website', description: null, status: 'ACTIVE', createdAt: '2026-01-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })

    renderPage()

    expect(await screen.findByRole('heading', { name: 'Acme Inc' })).toBeInTheDocument()
    expect(await screen.findByText('3')).toBeInTheDocument()
    expect(await screen.findByText('Website')).toBeInTheDocument()
  })

  it('shows an empty state when the organization has no projects', async () => {
    vi.mocked(organizationsApi.get).mockResolvedValue({
      id: 'org-1',
      name: 'Acme Inc',
      createdAt: '2026-01-01T00:00:00Z',
    })
    vi.mocked(organizationsApi.listMembers).mockResolvedValue({
      content: [],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })
    vi.mocked(projectsApi.list).mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 })

    renderPage()

    expect(await screen.findByText('No projects yet in this organization.')).toBeInTheDocument()
  })

  it('shows a not-a-member message without confirming the organization exists', async () => {
    vi.mocked(organizationsApi.get).mockRejectedValue(
      new ApiError(404, {
        code: 'ORGANIZATION_NOT_FOUND',
        message: "No organization found with id 'org-1'",
        timestamp: '2026-01-01T00:00:00Z',
        path: '/api/v1/organizations/org-1',
        details: [],
      }),
    )

    renderPage()

    expect(await screen.findByRole('alert')).toHaveTextContent("doesn't exist, or you're not a member")
  })
})
