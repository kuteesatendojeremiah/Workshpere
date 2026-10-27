# WorkSphere — Project Instructions for Claude

## What this project is
WorkSphere is a multi-tenant SaaS project-management platform (organizations, teams, projects,
tasks, Kanban, sprints, issues, comments, notifications, audit log, reporting, billing).
The full specification is in `docs/WorkSphere-Specification.pdf`. Treat it as the source of truth.
If a request conflicts with the spec, point out the conflict before writing code.

This is also a LEARNING project. I am the student building it. Explain the key decisions
in plain language as you go, and keep the code readable rather than clever.

## Tech stack
- Backend: Java 21, Spring Boot 3, Maven, Spring Data JPA, Spring Security, Bean Validation
- Database: PostgreSQL, migrations with Flyway
- Frontend: React + TypeScript + Vite (in `frontend/`)
- Testing: JUnit 5, Mockito, Testcontainers
- Later phases only: Redis, WebSockets, Kafka, object storage (MinIO), Docker, GitHub Actions
- Do NOT add Redis, Kafka, microservices, Elasticsearch or AI features until I ask for that phase.

## Repository layout
```
backend/   src/main/java/com/worksphere/...   (Spring Boot)
frontend/  src/  tests/                        (React + TypeScript)
infra/     docker/  nginx/
docs/      architecture/ api/ database/ security/ decisions/ (ADRs)
.github/workflows/
docker-compose.yml
README.md
```

## Backend architecture rules
- Modular monolith. One package per module under `com.worksphere`:
  common (error, security, audit, pagination, web), identity, organization, team, project,
  task, sprint, issue, notification, file, billing, reporting.
- Layers inside each module: controller -> application service -> domain -> repository.
- Controllers contain NO business logic. They validate input, call a service, return a DTO.
- Never expose JPA entities in API responses. Use explicit request/response DTOs.
- Modules must not reach into each other's repositories. Call the other module's service.
- API paths follow `/api/v1/...` as in section 12 of the spec.
- Collections are always paginated, with explicit filtering and sorting.
- Errors use one consistent JSON format with stable error codes. Never leak stack traces.

## Security rules (non-negotiable)
- Every protected endpoint enforces authorization on the server side.
- Tenant isolation: NEVER trust an organization_id sent by the client. Derive or verify the
  tenant from the authenticated user's membership. Every tenant-owned query filters by tenant.
- Passwords hashed with BCrypt (or Argon2). Short-lived access tokens + refresh-token rotation.
- No secrets in code or Git. Use environment variables and a `.env.example` file.
- Validate all input at the API boundary. Use parameterized queries / JPA only.
- Write audit-log entries for security-sensitive and business-sensitive actions.

## Database rules
- Every schema change is a new Flyway migration. Never edit a migration already applied.
- Add foreign keys, NOT NULL, unique constraints and indexes for real invariants and lookups.
- Use transactions around operations that must succeed or fail together.
- Use optimistic locking (@Version) on entities likely to be edited concurrently (e.g. tasks).

## Definition of done (check every feature against this)
1. Requirement is clear (refer to the spec section).
2. Database changes have a migration.
3. API has validation and authorization.
4. Business logic has unit tests.
5. At least one integration test with Testcontainers + PostgreSQL.
6. Negative security tests where relevant (wrong org, missing permission, bad token).
7. Frontend handles loading, empty, error and success states.
8. Audit/notification behaviour is defined where relevant.
9. OpenAPI documentation is updated.
10. All tests pass (`./mvnw verify` in backend, `npm test` in frontend).

## How to work with me
- Work on ONE step at a time. Before writing code for a new step, show me a short plan
  (files to create/change, tables, endpoints, tests) and wait for my approval.
- After finishing a step: run the tests, summarise what was built, list anything left
  undone, and suggest a commit message. Do not move to the next step on your own.
- Keep changes small enough that I can review them.
- If something in the spec is unclear or seems wrong, ask me rather than guessing.
- When you make an important architectural choice, write a short ADR in `docs/decisions/`
  (Context, Decision, Alternatives, Consequences, Security impact, Operational impact).

## Progress tracker
Update this list as steps are completed.
- [x] 1 Repository + coding standards (repo itself not yet pushed to GitHub - deferred by user)
- [x] 2 Spring Boot skeleton
- [x] 3 PostgreSQL + Flyway
- [x] 4 User / account module
- [x] 5 Authentication
- [x] 6 Organization + membership
- [x] 7 Roles + permissions
- [x] 8 Teams
- [x] 9 Projects
- [x] 10 Tasks
- [x] 11 Comments + labels
- [x] 12 Kanban
- [x] 13 Sprints
- [x] 14 Issues
- [x] 15 Audit logs
- [x] 16 Notifications
- [x] 17 Frontend dashboard (login/register, org selector, dashboard, projects list, project
      overview, Kanban board, task details)
- [x] 18 Testing hardening
- [ ] 19 WebSockets
- [ ] 20 Redis
- [ ] 21 Kafka + outbox
- [ ] 22 Files + email
- [ ] 23 Billing / webhooks
- [ ] 24 Observability
- [ ] 25 Docker
- [ ] 26 CI/CD
- [ ] 27 Cloud deployment
- [ ] 28 Performance / security hardening
- [ ] 29 Selected microservice extraction
- [ ] 30 Final documentation + interview prep
