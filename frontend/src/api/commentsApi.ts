import { apiClient } from "./client";
import type { CommentResponse, PageResponse } from "./types";

export const commentsApi = {
  list: (taskId: string) =>
    apiClient.get<PageResponse<CommentResponse>>(
      `/api/v1/tasks/${taskId}/comments?size=100&sort=createdAt,asc`,
    ),
  create: (taskId: string, body: string) =>
    apiClient.post<CommentResponse>(`/api/v1/tasks/${taskId}/comments`, {
      body,
    }),
  delete: (taskId: string, commentId: string) =>
    apiClient.delete<void>(`/api/v1/tasks/${taskId}/comments/${commentId}`),
};
