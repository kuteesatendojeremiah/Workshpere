import { apiClient } from "./client";
import type {
  PageResponse,
  TaskPriority,
  TaskResponse,
  TaskStatus,
} from "./types";

export interface UpdateTaskInput {
  title: string;
  description: string | null;
  priority: TaskPriority;
  dueDate: string | null;
  version: number;
}

export const tasksApi = {
  listByStatus: (projectId: string, status: TaskStatus) =>
    apiClient.get<PageResponse<TaskResponse>>(
      `/api/v1/projects/${projectId}/tasks?status=${status}&size=200&sort=position,asc`,
    ),
  create: (projectId: string, title: string, priority: TaskPriority) =>
    apiClient.post<TaskResponse>(`/api/v1/projects/${projectId}/tasks`, {
      title,
      priority,
    }),
  changeStatus: (
    taskId: string,
    status: TaskStatus,
    targetPosition: number,
    version: number,
  ) =>
    apiClient.post<TaskResponse>(`/api/v1/tasks/${taskId}/status`, {
      status,
      targetPosition,
      version,
    }),
  get: (taskId: string) =>
    apiClient.get<TaskResponse>(`/api/v1/tasks/${taskId}`),
  update: (taskId: string, input: UpdateTaskInput) =>
    apiClient.put<TaskResponse>(`/api/v1/tasks/${taskId}`, input),
  assign: (taskId: string, assigneeId: string | null, version: number) =>
    apiClient.post<TaskResponse>(`/api/v1/tasks/${taskId}/assignee`, {
      assigneeId,
      version,
    }),
  delete: (taskId: string) => apiClient.delete<void>(`/api/v1/tasks/${taskId}`),
};
