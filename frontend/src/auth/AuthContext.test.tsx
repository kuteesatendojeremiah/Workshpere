import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getAccessToken, notifyUnauthorized } from '../api/tokenStore'
import { AuthProvider, useAuth } from './AuthContext'
import { ProtectedRoute } from './ProtectedRoute'

vi.mock('../api/authApi', () => ({
  authApi: { login: vi.fn(), logout: vi.fn() },
}))
vi.mock('../api/usersApi', () => ({
  usersApi: { getProfile: vi.fn() },
}))

const { authApi } = await import('../api/authApi')
const { usersApi } = await import('../api/usersApi')

const fakeJwt = `x.${btoa(JSON.stringify({ sub: 'user-1' }))}.y`

// A tiny harness: a login button, a logout button, and a protected page behind ProtectedRoute.
function Harness() {
  const { login, logout, isAuthenticated } = useAuth()
  return (
    <div>
      <p>{isAuthenticated ? 'signed in' : 'signed out'}</p>
      <button onClick={() => login('a@example.com', 'password123').catch(() => undefined)}>log in</button>
      <button onClick={() => logout()}>log out</button>
    </div>
  )
}

function renderApp(initialPath: string) {
  render(
    <MemoryRouter initialEntries={[initialPath]}>
      <AuthProvider>
        <Harness />
        <Routes>
          <Route path="/login" element={<div>Login screen</div>} />
          <Route
            path="/secret"
            element={
              <ProtectedRoute>
                <div>Secret page</div>
              </ProtectedRoute>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ProtectedRoute and AuthContext', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: fakeJwt,
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
    })
    vi.mocked(authApi.logout).mockResolvedValue(undefined as never)
    vi.mocked(usersApi.getProfile).mockResolvedValue({
      id: 'user-1',
      email: 'a@example.com',
      displayName: 'Ann',
      emailVerified: true,
      createdAt: '2026-01-01T00:00:00Z',
    })
  })

  it('redirects a signed-out visitor to the login screen instead of showing the page', () => {
    renderApp('/secret')

    expect(screen.getByText('Login screen')).toBeInTheDocument()
    expect(screen.queryByText('Secret page')).not.toBeInTheDocument()
  })

  it('shows the protected page once signed in, and stores the access token', async () => {
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'log in' }))

    await waitFor(() => expect(screen.getByText('signed in')).toBeInTheDocument())
    expect(getAccessToken()).toBe(fakeJwt)
    expect(usersApi.getProfile).toHaveBeenCalledWith('user-1')
  })

  it('stays signed out and clears the token when the login request fails', async () => {
    vi.mocked(authApi.login).mockRejectedValue(new Error('bad credentials'))
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'log in' }))

    await waitFor(() => expect(authApi.login).toHaveBeenCalled())
    expect(screen.getByText('signed out')).toBeInTheDocument()
    expect(getAccessToken()).toBeNull()
  })

  it('stays signed out when the access token has no readable subject', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'not-a-jwt',
      refreshToken: 'refresh-token',
      tokenType: 'Bearer',
    })
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'log in' }))

    await waitFor(() => expect(authApi.login).toHaveBeenCalled())
    expect(screen.getByText('signed out')).toBeInTheDocument()
    expect(getAccessToken()).toBeNull()
    expect(usersApi.getProfile).not.toHaveBeenCalled()
  })

  it('logout revokes the refresh token on the server and clears the session', async () => {
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'log in' }))
    await waitFor(() => expect(screen.getByText('signed in')).toBeInTheDocument())

    await userEvent.click(screen.getByRole('button', { name: 'log out' }))

    await waitFor(() => expect(screen.getByText('signed out')).toBeInTheDocument())
    expect(authApi.logout).toHaveBeenCalledWith('refresh-token')
    expect(getAccessToken()).toBeNull()
  })

  it('logout still clears the session when the server call fails', async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new Error('network down'))
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'log in' }))
    await waitFor(() => expect(screen.getByText('signed in')).toBeInTheDocument())

    await userEvent.click(screen.getByRole('button', { name: 'log out' }))

    await waitFor(() => expect(screen.getByText('signed out')).toBeInTheDocument())
    expect(getAccessToken()).toBeNull()
  })

  it('a 401 from the API signs the user out', async () => {
    renderApp('/login')
    await userEvent.click(screen.getByRole('button', { name: 'log in' }))
    await waitFor(() => expect(screen.getByText('signed in')).toBeInTheDocument())

    act(() => notifyUnauthorized())

    await waitFor(() => expect(screen.getByText('signed out')).toBeInTheDocument())
    expect(getAccessToken()).toBeNull()
  })

  it('useAuth throws a clear error outside an AuthProvider', () => {
    const spy = vi.spyOn(console, 'error').mockImplementation(() => undefined)
    expect(() => render(<Harness />)).toThrow('useAuth must be used within an AuthProvider')
    spy.mockRestore()
  })
})
