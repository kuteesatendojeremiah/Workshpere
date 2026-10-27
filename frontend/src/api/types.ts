// Mirrors the backend's consistent error shape (GlobalExceptionHandler / ErrorResponse.java).
export interface FieldViolation {
  field: string
  message: string
}

export interface ApiErrorBody {
  code: string
  message: string
  timestamp: string
  path: string
  details: FieldViolation[]
}

// Mirrors com.worksphere.identity.RegisterRequest / UserProfileResponse.
export interface RegisterRequest {
  email: string
  password: string
  displayName: string
}

export interface UserProfileResponse {
  id: string
  email: string
  displayName: string
  emailVerified: boolean
  createdAt: string
}

// Mirrors com.worksphere.identity.LoginRequest / TokenPairResponse.
export interface LoginRequest {
  email: string
  password: string
}

export interface TokenPairResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
}

// Mirrors com.worksphere.common.pagination.PageResponse.
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// Mirrors com.worksphere.organization.OrganizationResponse.
export interface OrganizationResponse {
  id: string
  name: string
  createdAt: string
}

// Mirrors com.worksphere.organization.OrganizationMemberResponse.
export interface OrganizationMemberResponse {
  userId: string
  email: string
  displayName: string
  role: string
  joinedAt: string
}

// Mirrors com.worksphere.project.ProjectResponse.
export interface ProjectResponse {
  id: string
  organizationId: string
  name: string
  description: string | null
  status: 'ACTIVE' | 'ARCHIVED'
  createdAt: string
}

// Mirrors com.worksphere.project.ProjectMemberResponse.
export interface ProjectMemberResponse {
  userId: string
  email: string
  displayName: string
  joinedAt: string
}

/** The two roles that can actually manage projects, per the seeded RBAC data
 * (V16__seed_project_permissions.sql grants MANAGE_PROJECTS to these two only). Duplicated
 * here deliberately for a snappier UI (hide actions that would just 403) - the backend
 * remains the real enforcement point regardless of what this says. */
export const PROJECT_MANAGING_ROLES = ['ORGANIZATION_OWNER', 'PROJECT_MANAGER']

// Mirrors com.worksphere.task.TaskStatus. Order matters - it's the left-to-right column order.
export const TASK_STATUSES = ['TODO', 'IN_PROGRESS', 'IN_REVIEW', 'DONE'] as const
export type TaskStatus = (typeof TASK_STATUSES)[number]

export const TASK_STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To Do',
  IN_PROGRESS: 'In Progress',
  IN_REVIEW: 'In Review',
  DONE: 'Done',
}

/** Mirrors TaskStatus.canTransitionTo: forward one column at a time, backward any distance.
 * Duplicated client-side so the board can refuse an invalid drop instead of letting it bounce
 * back after a 409 from the server, which remains the real enforcement point either way. */
export function canTransitionTo(from: TaskStatus, to: TaskStatus): boolean {
  const fromIndex = TASK_STATUSES.indexOf(from)
  const toIndex = TASK_STATUSES.indexOf(to)
  return toIndex === fromIndex || toIndex < fromIndex || toIndex === fromIndex + 1
}

// Mirrors com.worksphere.task.TaskPriority.
export const TASK_PRIORITIES = ['LOW', 'MEDIUM', 'HIGH', 'URGENT'] as const
export type TaskPriority = (typeof TASK_PRIORITIES)[number]

// Mirrors com.worksphere.task.TaskResponse.
export interface TaskResponse {
  id: string
  projectId: string
  title: string
  description: string | null
  status: TaskStatus
  position: number
  priority: TaskPriority
  assigneeId: string | null
  dueDate: string | null
  version: number
  createdAt: string
  updatedAt: string
}

// Mirrors com.worksphere.task.CommentResponse.
export interface CommentResponse {
  id: string
  taskId: string
  authorId: string
  authorDisplayName: string
  body: string
  createdAt: string
}

// Mirrors com.worksphere.task.LabelResponse.
export interface LabelResponse {
  id: string
  projectId: string
  name: string
  color: string
}

// Mirrors com.worksphere.file.FileResponse.
export interface FileResponse {
  id: string
  taskId: string
  filename: string
  contentType: string
  sizeBytes: number
  uploadedBy: string
  createdAt: string
}
