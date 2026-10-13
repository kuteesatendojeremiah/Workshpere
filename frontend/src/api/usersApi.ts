import { apiClient } from './client'
import type { UserProfileResponse } from './types'

export const usersApi = {
  getProfile: (userId: string) => apiClient.get<UserProfileResponse>(`/api/v1/users/${userId}`),
}
