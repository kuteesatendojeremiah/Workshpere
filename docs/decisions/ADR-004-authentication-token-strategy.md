# ADR-004: Authentication / Token Strategy

## Context

WorkSphere needs stateless authentication for a REST API: short-lived credentials that don't
require a server-side session store, a way to renew those credentials without forcing the user
to log in again constantly, a way to revoke access (logout, or in response to suspected theft),
and a self-service password reset flow. Section 11.1 of the spec calls for short-lived access
tokens, refresh-token rotation/revocation, and expiring single-use password-reset tokens.

## Decision

- **Access tokens** are JWTs (HS256, signed with a secret from `JWT_SECRET`), short-lived
  (`JWT_ACCESS_TOKEN_TTL_MINUTES`, default 15), carrying only the user id as the subject claim.
  They are verified on every request by `JwtAuthenticationFilter` with no database lookup.
- **Refresh tokens** are opaque random values (not JWTs), long-lived
  (`JWT_REFRESH_TOKEN_TTL_DAYS`, default 30), stored as a SHA-256 hash in `refresh_tokens` so a
  database leak never hands out a usable token. Each use **rotates** the token: the presented
  token is revoked and a new one issued in the same response.
- **Reuse detection:** if a refresh token that's already been revoked (i.e. already rotated away)
  is presented again, that's a signal it may have been stolen and replayed — every other active
  refresh token for that user is revoked too, not just the one request rejected.
- **Password reset** follows the same hashed-single-use-token pattern as Step 4's email
  verification tokens (1 hour TTL), and additionally revokes all of the user's active refresh
  tokens on a successful reset, forcing re-login everywhere.
- **Rate limiting is deferred, not skipped.** `AuthService` calls a `LoginAttemptGuard` seam
  (`beforeAttempt`/`onFailure`/`onSuccess`) around every login; today's implementation is a
  no-op, and Step 20 (Redis) replaces it with real per-email/IP throttling without touching
  `AuthService` itself.
- Login does **not** require `email_verified = true`. Nothing else currently depends on
  verification status, so gating login on it now would be an unused restriction with no
  corresponding feature behind it.

## Alternatives Considered

- **Server-side sessions (session cookie + session store).** Rejected for the API itself: it
  reintroduces server-side state we'd otherwise need Redis for immediately (Step 20 is later),
  and doesn't fit a pure REST API as cleanly as bearer tokens.
- **JWT refresh tokens (instead of opaque + DB-backed).** Rejected: a self-contained JWT can't be
  revoked before it expires without a server-side blocklist — at which point we're maintaining
  server-side state anyway, so an opaque DB-backed token is simpler and gives us real revocation
  and reuse detection for free.
- **No rotation (same refresh token reused until expiry).** Rejected: a static long-lived token
  is a bigger prize if it leaks, and rotation is what makes reuse detection possible in the first
  place — reuse of a *specific* token value is a clear signal, reuse of nothing is not.
- **Full OAuth2 / external identity provider.** Rejected per the spec's explicit non-goals: "Do
  not implement a custom identity provider" cuts the other way too — don't adopt the complexity
  of a generalized OAuth2 authorization server for a single first-party client either.

## Consequences

- Access-token validation needs no database round trip, keeping protected endpoints fast, but it
  also means a compromised access token is valid until it naturally expires (hence keeping the
  TTL short - 15 minutes).
- Every login/refresh/logout/password-reset path is covered by integration tests
  (`LoginIntegrationTest`, `PasswordResetIntegrationTest`) exercising real HTTP + a real database,
  plus `AuthServiceTest` unit tests for the rotation/reuse-detection logic in isolation.
- `GET`/`PUT /api/v1/users/{id}` (added unprotected in Step 4) are now genuinely protected:
  requests need a valid access token *and* the token's subject must match the path id, enforced
  in `UserController` via `AccessDeniedException` -> the existing `GlobalExceptionHandler`.

## Security Impact

This is the core of WorkSphere's security model from here forward: every future protected
endpoint relies on the same `JwtAuthenticationFilter` + `SecurityConfig` to establish who the
caller is. Credential checks (login) give identical error responses for "no such email" and
"wrong password", preventing account enumeration. Password reset request similarly always
responds the same way regardless of whether the email exists.

## Operational Impact

`JWT_SECRET` must be a long, random, environment-specific value in any real deployment (the
`application.yml` default is explicitly a local-dev-only placeholder) - rotating it immediately
invalidates every outstanding access token (and, since refresh tokens aren't signed with it,
does *not* force a full re-login - only a slightly larger window where expired access tokens are
briefly rejected on their final use, until the client calls `/auth/refresh`).
