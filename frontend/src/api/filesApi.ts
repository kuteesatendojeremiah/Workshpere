import { apiClient } from './client'
import type { FileResponse, PageResponse } from './types'

export const filesApi = {
  list: (taskId: string) =>
    apiClient.get<PageResponse<FileResponse>>(`/api/v1/tasks/${taskId}/attachments?size=100&sort=createdAt,asc`),

  upload: (taskId: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return apiClient.postForm<FileResponse>(`/api/v1/tasks/${taskId}/attachments`, form)
  },

  // Downloads need the Authorization header, so a plain <a href> cannot be used: fetch the bytes, then save them.
  download: (fileId: string) => apiClient.getBlob(`/api/v1/attachments/${fileId}/download`),

  delete: (fileId: string) => apiClient.delete<void>(`/api/v1/attachments/${fileId}`),
}
