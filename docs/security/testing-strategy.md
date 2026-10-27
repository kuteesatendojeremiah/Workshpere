# Testing strategy

How WorkSphere is tested, and what each kind of test protects. Written at Step 18 (testing hardening).

## The layers

| Layer | Tools | What it proves | Where |
|---|---|---|---|
| Unit tests | JUnit 5, Mockito | Business rules in services and domain classes, with no database | `*ServiceTest`, `TaskStatusTest` |
| Integration tests | Spring Boot, MockMvc, Testcontainers + PostgreSQL | The real stack end to end: HTTP, security filter, validation, JPA, Flyway migrations | `*IntegrationTest` |
| Negative security tests | same as integration | Things that must be refused, and that nothing changed after the refusal | `NegativeSecurityIntegrationTest`, `CrossTenantIsolationIntegrationTest` |
| Frontend tests | Vitest, Testing Library | Each page's loading, empty, error and success states; auth guard behaviour | `frontend/src/**/*.test.tsx` |

Integration tests run against a real PostgreSQL container, not an in-memory database, so Flyway
migrations, constraints and indexes are exercised exactly as in production. Docker must be running.

## Tenant isolation (the most important guarantee)

`CrossTenantIsolationIntegrationTest` creates two organizations. A user who owns organization B
(valid token, full owner of B) calls every route that touches organization A's data and must get
403 or 404 each time, with no victim data in the response body. Afterwards the test reads the
victim's data as the victim and checks nothing was changed.

It includes "confused deputy" calls: the attacker's own organization id in the URL combined with
the victim's project or team id. This is the classic way tenant checks get bypassed.

**Rule for new work:** when you add a tenant-owned endpoint, add a line for it to
`attackRequests` in that test. The test asserts a minimum route count so the list cannot silently
shrink.

## Other negative tests

- Missing permission: a plain member cannot create/edit/archive projects, create sprints, create
  teams or remove members (403 `MISSING_PERMISSION`), and the data is unchanged afterwards.
- Tokens: tampered signature, `alg: none`, token signed with another key, garbage or wrong-scheme
  headers, expired tokens (all 401 `UNAUTHENTICATED`, with no stack trace).
- Refresh tokens: rotation, reuse of a rotated token is rejected and revokes the whole chain,
  logout revokes, expired tokens are rejected (`LoginIntegrationTest`, `AuthServiceTest`).
- Mass assignment: client-supplied `organizationId`, `status`, `projectId` are ignored.
- Concurrency: updating a task with a stale `version` is rejected (optimistic locking, HTTP 409).

## Coverage gate

JaCoCo runs in `./mvnw verify` and the build fails if overall coverage drops below
**90% lines / 78% branches** (`backend/pom.xml`, execution `check`). At Step 18 the measured level
is about 94% lines / 83% branches, so the gate blocks regressions without forcing busywork.
Raise the numbers as coverage grows. The report is at `backend/target/site/jacoco/index.html`.

Coverage is a floor, not a goal. A line being executed does not mean its behaviour is checked,
which is why the security tests above assert on responses and on unchanged data.

## Frontend notes

- Pages are tested with API modules mocked, so the tests are fast and need no backend.
- `src/auth/AuthContext.test.tsx` covers the route guard (signed-out visitors are redirected),
  login success and failure, logout (also when the server call fails), and that a 401 from the
  API signs the user out.
- Known design point: the client does **not** silently refresh an expired access token; a 401
  signs the user out. Silent refresh is a possible later improvement.
- Vitest is capped at 2 workers (`vite.config.ts`) because spawning many jsdom workers overloads
  some machines.

## Running everything

```
cd backend  && ./mvnw verify      # needs Docker running
cd frontend && npm test
```
