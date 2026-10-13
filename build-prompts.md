# WorkSphere — Prompts to give Claude, step by step

Paste ONE prompt at a time. Review the plan Claude shows you, approve it, let it build,
check that the tests pass, commit to Git, then move to the next prompt.
Tip: start each new step with a fresh session so it reads CLAUDE.md cleanly.

---

## Before you start
1. Create an empty folder `worksphere/` and run `git init` inside it.
2. Put `CLAUDE.md` in the root of that folder.
3. Create `docs/` and copy the specification PDF in as `docs/WorkSphere-Specification.pdf`.
4. Install: Java 21, Maven (or use the wrapper), Node.js LTS, Docker Desktop, Git.

---

## Stage 1 — Foundation

**Step 1 — Repository**
> Read CLAUDE.md and the spec in docs/. Set up the repository structure from Appendix A:
> backend/, frontend/, infra/, docs/ subfolders, .github/workflows/, a .gitignore,
> .editorconfig, .env.example and a README with setup instructions. Write ADR-001
> (modular monolith first). Show me the plan first.

**Step 2 — Spring Boot skeleton**
> Create the Spring Boot 3 / Java 21 backend skeleton in backend/ with the package structure
> from section 9.2. Include the common module: global exception handler with a consistent
> error format, pagination helpers, and a health endpoint. Add a docker-compose.yml that
> starts only PostgreSQL for now. Plan first.

**Step 3 — Database + migrations**
> Configure PostgreSQL and Flyway. Create the first migration for the users table only.
> Set up Testcontainers so integration tests run against a real PostgreSQL. Write ADR-002.
> Plan first.

## Stage 2 — Users and security

**Step 4 — User accounts**
> Build the identity module's user part: registration with validation, email-verification
> token, and profile view/update. Passwords hashed with BCrypt. Include unit and integration
> tests. Plan first.

**Step 5 — Authentication**
> Add login, logout, short-lived JWT access tokens, refresh-token rotation and revocation,
> and password reset with expiring single-use tokens (section 11.1). Add rate limiting on
> login later (Redis phase) but leave a clear hook for it. Write ADR-004. Include negative
> tests: wrong password, expired token, revoked token. Plan first.

**Step 6 — Organizations + membership**
> Build the organization module: create organization (creator becomes owner), invitations,
> accept invitation, list members. Implement tenant context derived from the authenticated
> user (section 10.3) — never trust a client-sent organization_id. Write ADR-003. Include a
> test proving a user from Org A cannot read Org B. Plan first.

**Step 7 — Roles + permissions**
> Implement RBAC: roles, permissions, role_permissions, seeded with the roles from section 3.
> Add a reusable way to check permissions in services. Include negative tests for privilege
> escalation (a member calling an owner-only action). Plan first.

## Stage 3 — Core product

**Step 8 — Teams**
> Build the team module: CRUD under an organization, add/remove members, team roles.
> Tenant-scoped and permission-checked. Tests including cross-tenant negative test. Plan first.

**Step 9 — Projects**
> Build the project module: create, update, archive, project members, settings. Endpoints per
> sections 12–13. Tests. Plan first.

**Step 10 — Tasks**
> Build the task module: CRUD, assignment, status, priority, due dates, dependencies.
> Use optimistic locking. Paginated listing with filtering and sorting. Tests, including
> BOLA test (changing a task ID in the URL to another org's task must fail). Plan first.

**Step 11 — Comments + labels**
> Add comments (with @mentions stored for later notifications) and labels on tasks. Tests.
> Plan first.

**Step 12 — Kanban**
> Add Kanban columns, task ordering within columns and allowed status transitions. The server
> decides final order — do not trust frontend ordering (section 23). Tests. Plan first.

**Step 13 — Sprints**
> Build sprints: backlog, sprint planning, start/complete, goals, story points, and velocity
> and burndown data endpoints. Tests. Plan first.

**Step 14 — Issues**
> Build issue tracking: severity, priority, environment, lifecycle, link to tasks. Tests.
> Plan first.

**Step 15 — Audit logs**
> Implement the audit log with the fields in section 22, a request/correlation ID, and record
> the security- and business-sensitive actions built so far. Add GET audit-logs for owners.
> Plan first.

**Step 16 — Notifications**
> Build in-app notifications (stored in the database) for assignment, mentions and status
> changes, with list and mark-read endpoints. No email or real-time yet. Plan first.

## Stage 4 — Frontend and testing

**Step 17 — Frontend**
> Create the React + TypeScript frontend in frontend/. Typed API client, central auth state,
> and the screens from section 14.1 in this order: login/register, organization selector,
> dashboard, projects list, project overview, Kanban board, task details. Every screen
> handles loading, empty, error and success states and is keyboard accessible. Do it in
> small batches of screens — plan the first batch now.

**Step 18 — Testing hardening**
> Review the whole codebase against section 24. List missing tests, especially every item in
> 24.1 (required negative tests), then write them. Add one end-to-end browser test:
> login -> create project -> create task -> complete it. Report coverage. Plan first.

## Stage 5 — Advanced features (only after Stages 1–4 are solid)

**Step 19 — WebSockets**
> Add WebSocket real-time updates for notifications and Kanban changes, sent only to users
> allowed to see them (section 15). Plan first.

**Step 20 — Redis**
> Add Redis: cache project summaries with invalidation on changes, and rate limiting on login
> and sensitive endpoints (section 17). Write ADR-006. Plan first.

**Step 21 — Kafka + outbox**
> Add Kafka with the transactional outbox pattern (section 16.3). Publish the events in
> section 16.1. Consumers must be idempotent, with retries and a dead-letter topic.
> Write ADR-007 and ADR-008. Explain the delivery semantics in plain language. Plan first.

**Step 22 — Files + email**
> Add file attachments using MinIO object storage (metadata in PostgreSQL), with size/type
> validation and authorization before download. Add email notifications through a Kafka
> consumer, using a local mail-dev container. Write ADR-009. Plan first.

**Step 23 — Billing**
> Build plans (Free, Professional, Enterprise), plan limits, subscription state, and a payment
> webhook endpoint using a provider's test mode (e.g. Stripe test mode). Verify signatures and
> process webhooks idempotently. Test that a duplicate webhook does not duplicate state.
> Plan first.

## Stage 6 — Production readiness

**Step 24 — Observability**
> Add structured JSON logging, correlation IDs, readiness/liveness endpoints, and metrics via
> Spring Boot Actuator + Prometheus, with a basic Grafana dashboard in docker-compose.
> Plan first.

**Step 25 — Docker**
> Dockerize backend and frontend, add Nginx as reverse proxy, and make one command
> (`docker compose up`) start everything with seed demo data. Plan first.

**Step 26 — CI/CD**
> Create a GitHub Actions pipeline following section 27: compile, unit tests, integration
> tests, static checks, dependency scan, build artifact, build Docker image, image scan.
> Plan first.

**Step 27 — Cloud deployment**
> Help me deploy to a single cloud VM with Docker Compose and HTTPS (section 28). Write
> ADR-010 and a deployment runbook in docs/. Plan first.

**Step 28 — Hardening**
> Review the app against OWASP ASVS and the threat model in section 29. Produce a security
> checklist in docs/security/ showing each threat and where it is mitigated, fix any gaps,
> and run a basic load test on list/search/dashboard endpoints. Plan first.

## Stage 7 — Finish

**Step 29 — Service extraction (optional)**
> Explain whether extracting the notification service makes sense now, with the trade-offs.
> If I agree, extract it behind clear boundaries. Plan first.

**Step 30 — Documentation + interview prep**
> Produce the portfolio deliverables in section 34: architecture diagram, ER diagram, OpenAPI
> docs, README with demo credentials, testing report, security checklist, resume-ready
> project description, and a system-design explanation. Then quiz me on why each major
> technology was chosen (section 37).

---

## Useful prompts at any time
- "Explain what you just built as if I'm a beginner, then quiz me with 3 questions."
- "Check this step against the Definition of Done in CLAUDE.md and list anything missing."
- "Tests are failing — find the cause and explain it before fixing."
- "Update the progress tracker in CLAUDE.md."
