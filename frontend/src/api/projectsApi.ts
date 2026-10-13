import { apiClient } from './client'
import type { PageResponse, ProjectMemberResponse, ProjectResponse } from './types'

export const projectsApi = {
  list: (organizationId: string) =>
    apiClient.get<PageResponse<ProjectResponse>>(`/api/v1/organizations/${organizationId}/projects?size=50`),

  get: (organizationId: string, projectId: string) =>
    apiClient.get<ProjectResponse>(`/api/v1/organizations/${organizationId}/projects/${projectId}`),

  create: (organizationId: string, name: string, description?: string) =>
    apiClient.post<ProjectResponse>(`/api/v1/organizations/${organizationId}/projects`, { name, description }),

  update: (organizationId: string, projectId: string, name: string, description?: string) =>
    apiClient.put<ProjectResponse>(`/api/v1/organizations/${organizationId}/projects/${projectId}`, {
      name,
      description,
    }),

  archive: (organizationId: string, projectId: string) =>
    apiClient.post<ProjectResponse>(`/api/v1/organizations/${organizationId}/projects/${projectId}/archive`),

  listMembers: (organizationId: string, projectId: string) =>
    apiClient.get<PageResponse<ProjectMemberResponse>>(
      `/api/v1/organizations/${organizationId}/projects/${projectId}/members?size=50`,
    ),

  addMember: (organizationId: string, projectId: string, userId: string) =>
    apiClient.post<ProjectMemberResponse>(
      `/api/v1/organizations/${organizationId}/projects/${projectId}/members`,
      { userId },
    ),

  removeMember: (organizationId: string, projectId: string, userId: string) =>
    apiClient.delete<void>(`/api/v1/organizations/${organizationId}/projects/${projectId}/members/${userId}`),
}
