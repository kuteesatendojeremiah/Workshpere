import { afterEach, describe, expect, it, vi } from 'vitest'
import { apiClient, ApiError, NetworkError } from './client'
import { setAccessToken, setUnauthorizedHandler } from './tokenStore'

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

describe('apiClient', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
    setAccessToken(null)
    setUnauthorizedHandler(null)
  })

  it('attaches the bearer token when one is set', async () => {
    setAccessToken('test-token')
    const fetchMock = vi.fn().mockResolvedValue(jsonResponse(200, { ok: true }))
    vi.stubGlobal('fetch', fetchMock)

    await apiClient.get('/api/v1/whatever')

    const [, init] = fetchMock.mock.calls[0]
    expect((init.headers as Record<string, string>).Authorization).toBe('Bearer test-token')
  })

  it('throws ApiError with the backend error code on a non-2xx response', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(409, {
          code: 'EMAIL_ALREADY_REGISTERED',
          message: 'An account with that email already exists',
          timestamp: '2026-01-01T00:00:00Z',
          path: '/api/v1/auth/register',
          details: [],
        }),
      ),
    )

    await expect(apiClient.post('/api/v1/auth/register', {})).rejects.toSatisfy((error: unknown) => {
      expect(error).toBeInstanceOf(ApiError)
      expect((error as ApiError).code).toBe('EMAIL_ALREADY_REGISTERED')
      expect((error as ApiError).status).toBe(409)
      return true
    })
  })

  it('calls the unauthorized handler on a 401', async () => {
    const onUnauthorized = vi.fn()
    setUnauthorizedHandler(onUnauthorized)
    vi.stubGlobal(
      'fetch',
      vi.fn().mockResolvedValue(
        jsonResponse(401, {
          code: 'UNAUTHENTICATED',
          message: 'Authentication is required',
          timestamp: '2026-01-01T00:00:00Z',
          path: '/api/v1/users/me',
          details: [],
        }),
      ),
    )

    await expect(apiClient.get('/api/v1/users/me')).rejects.toBeInstanceOf(ApiError)
    expect(onUnauthorized).toHaveBeenCalledOnce()
  })

  it('throws NetworkError when fetch itself fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')))

    await expect(apiClient.get('/api/v1/whatever')).rejects.toBeInstanceOf(NetworkError)
  })
})
