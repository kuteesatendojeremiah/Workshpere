import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { RegisterPage } from './RegisterPage'
import { ApiError } from '../api/client'

vi.mock('../api/authApi', () => ({
  authApi: { register: vi.fn() },
}))

const { authApi } = await import('../api/authApi')

function renderRegisterPage() {
  render(
    <MemoryRouter>
      <RegisterPage />
    </MemoryRouter>,
  )
}

describe('RegisterPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('shows a success state after registering', async () => {
    vi.mocked(authApi.register).mockResolvedValue({
      id: 'user-1',
      email: 'new@example.com',
      displayName: 'New User',
      emailVerified: false,
      createdAt: '2026-01-01T00:00:00Z',
    })

    renderRegisterPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Name'), 'New User')
    await user.type(screen.getByLabelText('Email'), 'new@example.com')
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByRole('status')).toHaveTextContent('Account created')
  })

  it('maps field-level validation errors from the backend onto the right inputs', async () => {
    vi.mocked(authApi.register).mockRejectedValue(
      new ApiError(400, {
        code: 'VALIDATION_ERROR',
        message: 'Validation failed',
        timestamp: '2026-01-01T00:00:00Z',
        path: '/api/v1/auth/register',
        details: [{ field: 'password', message: 'password must be between 8 and 100 characters' }],
      }),
    )

    renderRegisterPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Name'), 'New User')
    await user.type(screen.getByLabelText('Email'), 'new@example.com')
    await user.type(screen.getByLabelText('Password'), 'short')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText('password must be between 8 and 100 characters')).toBeInTheDocument()
    expect(screen.getByLabelText('Password')).toHaveAttribute('aria-invalid', 'true')
  })

  it('shows the duplicate-email error on the email field', async () => {
    vi.mocked(authApi.register).mockRejectedValue(
      new ApiError(409, {
        code: 'EMAIL_ALREADY_REGISTERED',
        message: "An account with email 'new@example.com' already exists",
        timestamp: '2026-01-01T00:00:00Z',
        path: '/api/v1/auth/register',
        details: [],
      }),
    )

    renderRegisterPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Name'), 'New User')
    await user.type(screen.getByLabelText('Email'), 'new@example.com')
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Create account' }))

    expect(await screen.findByText("An account with email 'new@example.com' already exists")).toBeInTheDocument()
  })
})
