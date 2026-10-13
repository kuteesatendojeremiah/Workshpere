import { render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AuthProvider } from '../auth/AuthContext'
import { LoginPage } from './LoginPage'
import { ApiError } from '../api/client'

vi.mock('../api/authApi', () => ({
  authApi: { login: vi.fn(), logout: vi.fn() },
}))
vi.mock('../api/usersApi', () => ({
  usersApi: { getProfile: vi.fn() },
}))

const { authApi } = await import('../api/authApi')
const { usersApi } = await import('../api/usersApi')

function renderLoginPage() {
  render(
    <MemoryRouter initialEntries={['/login']}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<div>Home screen</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('logs in and navigates to the redirect target on success', async () => {
    const fakeJwt = `x.${btoa(JSON.stringify({ sub: 'user-1' }))}.y`
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: fakeJwt,
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
    })
    vi.mocked(usersApi.getProfile).mockResolvedValue({
      id: 'user-1',
      email: 'me@example.com',
      displayName: 'Me',
      emailVerified: false,
      createdAt: '2026-01-01T00:00:00Z',
    })

    renderLoginPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Email'), 'me@example.com')
    await user.type(screen.getByLabelText('Password'), 'password123')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    await waitFor(() => expect(screen.getByText('Home screen')).toBeInTheDocument())
    expect(authApi.login).toHaveBeenCalledWith({ email: 'me@example.com', password: 'password123' })
  })

  it('shows a generic incorrect-credentials message without distinguishing the cause', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError(401, {
        code: 'INVALID_CREDENTIALS',
        message: 'Invalid email or password',
        timestamp: '2026-01-01T00:00:00Z',
        path: '/api/v1/auth/login',
        details: [],
      }),
    )

    renderLoginPage()
    const user = userEvent.setup()

    await user.type(screen.getByLabelText('Email'), 'me@example.com')
    await user.type(screen.getByLabelText('Password'), 'wrong')
    await user.click(screen.getByRole('button', { name: 'Log in' }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Incorrect email or password.')
  })
})
