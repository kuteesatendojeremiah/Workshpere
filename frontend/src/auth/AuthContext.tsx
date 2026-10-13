import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react'
import { authApi } from '../api/authApi'
import { usersApi } from '../api/usersApi'
import { decodeJwtSubject } from '../api/jwt'
import { setAccessToken, setUnauthorizedHandler } from '../api/tokenStore'
import type { UserProfileResponse } from '../api/types'

interface AuthState {
  user: UserProfileResponse | null
  isAuthenticated: boolean
  isLoading: boolean
  login: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserProfileResponse | null>(null)
  const [refreshToken, setRefreshToken] = useState<string | null>(null)
  const [isLoading, setIsLoading] = useState(false)

  const clearSession = useCallback(() => {
    setAccessToken(null)
    setUser(null)
    setRefreshToken(null)
  }, [])

  const login = useCallback(async (email: string, password: string) => {
    setIsLoading(true)
    try {
      const tokens = await authApi.login({ email, password })
      setAccessToken(tokens.accessToken)
      setRefreshToken(tokens.refreshToken)

      const userId = decodeJwtSubject(tokens.accessToken)
      if (!userId) {
        throw new Error('Received an access token with no readable subject claim')
      }
      const profile = await usersApi.getProfile(userId)
      setUser(profile)
    } catch (error) {
      clearSession()
      throw error
    } finally {
      setIsLoading(false)
    }
  }, [clearSession])

  const logout = useCallback(async () => {
    if (refreshToken) {
      // Best-effort: the session is cleared client-side regardless of whether this succeeds.
      await authApi.logout(refreshToken).catch(() => undefined)
    }
    clearSession()
  }, [refreshToken, clearSession])

  // Registered once; the API client calls this on any 401 so an expired/invalid access token
  // always lands the user back at a logged-out state rather than a silently-broken screen.
  useMemo(() => {
    setUnauthorizedHandler(clearSession)
  }, [clearSession])

  const value = useMemo<AuthState>(
    () => ({ user, isAuthenticated: user !== null, isLoading, login, logout }),
    [user, isLoading, login, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider')
  }
  return context
}
