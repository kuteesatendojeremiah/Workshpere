// A plain (non-React) holder for the current access token, so the API client can read it
// synchronously on every request without importing React context (which would create a
// circular dependency: AuthContext uses the client, the client would need AuthContext).
// AuthContext is the only thing that calls the setters.

let accessToken: string | null = null
let onUnauthorized: (() => void) | null = null

export function getAccessToken(): string | null {
  return accessToken
}

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function setUnauthorizedHandler(handler: (() => void) | null): void {
  onUnauthorized = handler
}

export function notifyUnauthorized(): void {
  onUnauthorized?.()
}
