# ADR-001: Modular Monolith First

## Context

WorkSphere's spec (section 36, Architecture Evolution) lists a full target architecture that
eventually includes Redis, Kafka, WebSockets, and selected microservice extraction. Building
all of that from day one would force distributed-systems complexity (network failures, eventual
consistency, service discovery, distributed tracing) onto a project whose first job is to prove
out the core domain model (organizations, teams, projects, tasks, sprints, issues) and get
authentication, authorization and tenant isolation right.

## Decision

Build WorkSphere as a single Spring Boot application — a modular monolith — for the first
several stages. Code is organized into modules under `com.worksphere` by business domain
(identity, organization, team, project, task, sprint, issue, notification, file, billing,
reporting, plus a shared `common` module), each with its own controller → application service →
domain → repository layering. Modules call each other's services, never each other's
repositories directly, so the module boundaries stay real even though everything runs in one
process and one database.

## Alternatives Considered

- **Microservices from the start.** Rejected: pays the operational cost of service boundaries
  (network calls, independent deployability, distributed transactions) before there's any real
  need to scale teams or runtime independently. The spec explicitly calls this out as a
  non-goal for the first release.
- **Unstructured monolith (no module boundaries).** Rejected: would work short-term but makes
  it harder to reason about ownership and would make any future service extraction (spec
  section 29, "Service extraction") far more expensive, since there'd be no existing seam to
  cut along.

## Consequences

- Faster iteration early on: one build, one deploy unit, one database, no network calls between
  modules.
- Module boundaries are enforced by convention and code review (not by process isolation), so
  discipline is required to avoid modules reaching into each other's repositories.
- When/if a module (e.g. notifications, per spec section 29) is later extracted into its own
  service, the existing service-layer boundary is the extraction seam — the work is defining a
  network contract, not discovering one for the first time.

## Security Impact

None directly — authentication, authorization and tenant isolation are enforced the same way
regardless of process topology (server-side checks on every protected operation, tenant context
derived from the authenticated principal). A monolith does mean a single compromise surface
(one process, one database) rather than several; this is an accepted tradeoff for the current
stage and is revisited if/when services are extracted.

## Operational Impact

One deployable artifact and one database to run, migrate and monitor for now, which keeps local
development (`docker compose up`) and CI simple. Scaling is vertical/horizontally-replicated
at the whole-application level rather than per-module until an extraction happens.
