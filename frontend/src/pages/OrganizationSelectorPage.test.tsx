import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { OrganizationSelectorPage } from './OrganizationSelectorPage'

vi.mock('../api/organizationsApi', () => ({
  organizationsApi: { listMine: vi.fn(), create: vi.fn(), get: vi.fn(), listMembers: vi.fn() },
}))
vi.mock('../auth/AuthContext', () => ({
  useAuth: () => ({ user: { displayName: 'Me' }, logout: vi.fn() }),
}))

const { organizationsApi } = await import('../api/organizationsApi')

function renderPage() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/']}>
        <Routes>
          <Route path="/" element={<OrganizationSelectorPage />} />
          <Route path="/organizations/:organizationId" element={<div>Dashboard screen</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

describe('OrganizationSelectorPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows an empty-state message when the user has no organizations', async () => {
    vi.mocked(organizationsApi.listMine).mockResolvedValue({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    })

    renderPage()

    expect(await screen.findByText(/don't belong to any organization yet/i)).toBeInTheDocument()
  })

  it('lists organizations and navigates to the dashboard on click', async () => {
    vi.mocked(organizationsApi.listMine).mockResolvedValue({
      content: [{ id: 'org-1', name: 'Acme Inc', createdAt: '2026-01-01T00:00:00Z' }],
      page: 0,
      size: 50,
      totalElements: 1,
      totalPages: 1,
    })

    renderPage()
    const user = userEvent.setup()

    const orgButton = await screen.findByRole('button', { name: 'Acme Inc' })
    await user.click(orgButton)

    expect(await screen.findByText('Dashboard screen')).toBeInTheDocument()
  })

  it('creates an organization and navigates to its dashboard', async () => {
    vi.mocked(organizationsApi.listMine).mockResolvedValue({
      content: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
    })
    vi.mocked(organizationsApi.create).mockResolvedValue({
      id: 'org-2',
      name: 'New Org',
      createdAt: '2026-01-01T00:00:00Z',
    })

    renderPage()
    const user = userEvent.setup()

    await user.click(await screen.findByRole('button', { name: '+ New organization' }))
    await user.type(screen.getByLabelText('Organization name'), 'New Org')
    await user.click(screen.getByRole('button', { name: 'Create' }))

    await waitFor(() => expect(organizationsApi.create).toHaveBeenCalledWith('New Org'))
    expect(await screen.findByText('Dashboard screen')).toBeInTheDocument()
  })
})
