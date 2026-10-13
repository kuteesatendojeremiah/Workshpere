import { apiClient } from './client'
import type { OrganizationMemberResponse, OrganizationResponse, PageResponse } from './types'

export const organizationsApi = {
  listMine: () => apiClient.get<PageResponse<OrganizationResponse>>('/api/v1/organizations?size=50'),

  get: (organizationId: string) => apiClient.get<OrganizationResponse>(`/api/v1/organizations/${organizationId}`),

  create: (name: string) => apiClient.post<OrganizationResponse>('/api/v1/organizations', { name }),

  listMembers: (organizationId: string) =>
    apiClient.get<PageResponse<OrganizationMemberResponse>>(
      `/api/v1/organizations/${organizationId}/members?size=50`,
    ),
}
