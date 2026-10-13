# ADR-003: Tenant Isolation Strategy

## Context

WorkSphere is multi-tenant: many organizations share one deployment and one database. Section
10.3 of the spec is explicit that the server must derive or verify tenant context from the
authenticated principal's membership, and must never trust a client-supplied `organization_id`.
Getting this wrong is the single most damaging class of bug this project can have - a mistake
here means one customer can read or modify another's data (BOLA/IDOR, section 29's threat model).

## Decision

An organization id appearing in a URL (e.g. `/api/v1/organizations/{organizationId}/...`) is
**only a lookup key**, never a trusted authorization claim by itself. Every org-scoped operation
calls `OrganizationService.requireMembership(organizationId, callerUserId)` first, which does a
live database lookup for an `OrganizationMember` row joining *this specific* authenticated user
(from the JWT, which carries only a user id - never a role or org claim) to *this specific*
organization. If that row doesn't exist, the caller is rejected - whether because the
organization doesn't exist at all, or because it does but this user isn't in it. Those two cases
are deliberately indistinguishable (`OrganizationNotFoundException`, HTTP 404): a response never
confirms an organization's existence to someone outside it.

Every future module that owns organization-scoped data (team, project, task, sprint, issue, ...)
calls this same method - through `OrganizationService` (a public, cross-module call, never by
reaching into `OrganizationMemberRepository` directly) - rather than each reinventing its own
membership check. One tenant-isolation primitive, reused everywhere, is far easier to audit than
the same logic rewritten in every module.

## Alternatives Considered

- **Trust an `organizationId` claim embedded in the JWT.** Rejected: membership changes (added,
  removed, role changed) more often than a 15-minute access token's lifetime would reflect, and
  baking it into the token means a user removed from an org mid-session would keep access until
  their token expired. A live DB check is the only way to make removal take effect immediately.
- **Trust an `organization_id` field in the request body/header instead of re-deriving it from
  membership.** This is exactly what section 10.3 prohibits - rejected outright, since it would
  let a client simply assert membership in any organization it wants.
- **PostgreSQL Row-Level Security (RLS) as the primary mechanism.** The spec allows evaluating
  RLS as defense-in-depth (section 10.3), but rejected as the *primary* mechanism for now: it
  would require setting session-level context (e.g. `SET app.current_user_id`) on every
  connection and reasoning about policies across every future table, which is significant
  complexity for a first pass. The explicit `requireMembership` check is simpler to read, test,
  and reason about at this stage. Revisiting RLS as an additional layer is reasonable once more
  tenant-owned tables exist (noted for Step 28, security hardening).
- **404 vs. 403 for non-members.** Considered returning 403 (like `UserController`'s "not your
  own profile" check) for consistency, but decided 404 fits organizations specifically better:
  an organization's very existence is part of the tenant boundary being protected, whereas a
  user id existing is not treated as sensitive the same way.

## Consequences

- Every new org-scoped controller/service method must remember to call `requireMembership`
  first - there's no automatic enforcement (e.g. no AOP interceptor) at this stage. This is a
  manual discipline point worth re-examining once there are many more org-scoped modules.
- `LoginIntegrationTest`-style coverage now has a tenant-isolation equivalent:
  `OrganizationControllerIntegrationTest` proves a member of Org A gets 404 reading Org B.
- Pagination (`GET .../members`) is the first real use of `PageResponse`'s `from(Page<T>)`
  converter, now that JPA is in place.

## Security Impact

This is the concrete implementation of the project's core multi-tenancy guarantee. It composes
with Step 5's authentication: a valid JWT proves *who* the caller is; `requireMembership` proves
*what they're allowed to touch*. Neither alone is sufficient.

## Operational Impact

None beyond the usual: `organization_members` gets a row per membership, with a unique
`(organization_id, user_id)` constraint preventing duplicates, and an index on `user_id` for
"which organizations is this user in" lookups (useful once there's an organization-selector UI).
