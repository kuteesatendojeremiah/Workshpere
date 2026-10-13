import { apiClient } from './client'
import type { LoginRequest, RegisterRequest, TokenPairResponse, UserProfileResponse } from './types'

export const authApi = {
  register: (request: RegisterRequest) =>
    apiClient.post<UserProfileResponse>('/api/v1/auth/register', request, false),

  login: (request: LoginRequest) => apiClient.post<TokenPairResponse>('/api/v1/auth/login', request, false),

  logout: (refreshToken: string) => apiClient.post<void>('/api/v1/auth/logout', { refreshToken }, false),
}
