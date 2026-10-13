/** Minimal JWT payload decoding - no signature verification, since that's the server's job.
 * This only reads the `sub` claim (the user id) so the client knows who just logged in. */
export function decodeJwtSubject(token: string): string | null {
  const parts = token.split('.')
  if (parts.length !== 3) {
    return null
  }

  try {
    const payloadJson = atob(parts[1].replace(/-/g, '+').replace(/_/g, '/'))
    const payload = JSON.parse(payloadJson) as { sub?: string }
    return payload.sub ?? null
  } catch {
    return null
  }
}
