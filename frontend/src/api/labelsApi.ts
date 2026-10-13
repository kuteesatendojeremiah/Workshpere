import { apiClient } from "./client";
import type { LabelResponse } from "./types";

export const labelsApi = {
  listForProject: (projectId: string) =>
    apiClient.get<LabelResponse[]>(`/api/v1/projects/${projectId}/labels`),
  listForTask: (taskId: string) =>
    apiClient.get<LabelResponse[]>(`/api/v1/tasks/${taskId}/labels`),
  attach: (taskId: string, labelId: string) =>
    apiClient.post<LabelResponse>(`/api/v1/tasks/${taskId}/labels`, {
      labelId,
    }),
  detach: (taskId: string, labelId: string) =>
    apiClient.delete<void>(`/api/v1/tasks/${taskId}/labels/${labelId}`),
};
