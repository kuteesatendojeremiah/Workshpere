import type { ApiErrorBody } from './types'
import { getAccessToken, notifyUnauthorized } from './tokenStore'

const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

/** Thrown for every non-2xx response. Carries the backend's stable error `code`, so UI code
 * can branch on it (e.g. "INVALID_CREDENTIALS") rather than parsing human-readable messages. */
export class ApiError extends Error {
  readonly code: string
  readonly status: number
  readonly details: ApiErrorBody['details']

  constructor(status: number, body: ApiErrorBody) {
    super(body.message)
    this.code = body.code
    this.status = status
    this.details = body.details ?? []
  }
}

/** Thrown when the request never reached the server at all (offline, DNS, CORS, etc.). */
export class NetworkError extends Error {
  constructor(cause: unknown) {
    super('Could not reach the server. Check your connection and try again.')
    this.cause = cause
  }
}

interface RequestOptions {
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  body?: unknown
  // A multipart upload; the browser sets the Content-Type (with its boundary) itself.
  formData?: FormData
  // Read the response as a file instead of JSON.
  asBlob?: boolean
  auth?: boolean
}

async function request<T>(path: string, options: RequestOptions): Promise<T> {
  const headers: Record<string, string> = {}
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json'
  }
  if (options.auth !== false) {
    const token = getAccessToken()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
  }

  let response: Response
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      method: options.method,
      headers,
      body: options.formData ?? (options.body !== undefined ? JSON.stringify(options.body) : undefined),
    })
  } catch (cause) {
    throw new NetworkError(cause)
  }

  if (response.status === 204) {
    return undefined as T
  }

  if (options.asBlob && response.ok) {
    return (await response.blob()) as T
  }

  const text = await response.text()
  const json = text ? JSON.parse(text) : undefined

  if (!response.ok) {
    if (response.status === 401) {
      notifyUnauthorized()
    }
    throw new ApiError(response.status, json as ApiErrorBody)
  }

  return json as T
}

export const apiClient = {
  get: <T>(path: string, auth = true) => request<T>(path, { method: 'GET', auth }),
  post: <T>(path: string, body?: unknown, auth = true) => request<T>(path, { method: 'POST', body, auth }),
  put: <T>(path: string, body?: unknown, auth = true) => request<T>(path, { method: 'PUT', body, auth }),
  delete: <T>(path: string, auth = true) => request<T>(path, { method: 'DELETE', auth }),
  postForm: <T>(path: string, formData: FormData) => request<T>(path, { method: 'POST', formData }),
  getBlob: (path: string) => request<Blob>(path, { method: 'GET', asBlob: true }),
}
