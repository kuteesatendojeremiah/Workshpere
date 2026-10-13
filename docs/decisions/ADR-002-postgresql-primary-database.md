# ADR-002: PostgreSQL as Primary Transactional Database

## Context

WorkSphere needs a transactional store for a relational domain: organizations, memberships,
teams, projects, tasks with dependencies, sprints, issues, comments — entities with many
foreign-key relationships and invariants (uniqueness, not-null, referential integrity) that
matter for correctness, especially under multi-tenancy. The spec (sections 10 and 23) calls for
foreign keys, constraints, indexes, transactions, and both optimistic and row-level locking.

## Decision

Use PostgreSQL as the single primary datastore for all transactional data, managed through
Flyway migrations (versioned, forward-only SQL files under `backend/src/main/resources/db/migration`,
applied automatically on application startup) and accessed through Spring Data JPA. Schema
changes are never made by Hibernate auto-DDL (`ddl-auto` is fixed to `validate`) — Flyway is the
only thing allowed to change the schema, so what's in migrations is always exactly what's in the
database.

## Alternatives Considered

- **MongoDB / a document store.** Rejected: WorkSphere's domain is relational by nature (strict
  foreign keys between organizations → teams → projects → tasks, many-to-many memberships).
  Modeling that in a document store means re-implementing joins and referential integrity in
  application code instead of letting the database enforce them.
- **Hibernate auto-DDL (`ddl-auto: update`) instead of Flyway.** Rejected: auto-DDL can't express
  every change safely (renames, data backfills, dropping a column), has no history, and makes it
  impossible to review a schema change before it reaches a shared database. Flyway migrations are
  explicit, reviewable, and ordered.
- **Multiple databases (one per module).** Rejected for now: this is a modular monolith
  ([[ADR-001]]-equivalent reasoning) — module boundaries are enforced in code, not by separate
  databases. Splitting storage per module before there's a real scaling or ownership reason adds
  operational cost (multiple connections, no cross-module transactions) with no current benefit.

## Consequences

- Every schema change from here on is a new Flyway migration file; migrations already applied
  are never edited (per `CLAUDE.md`'s database rules).
- `ddl-auto: validate` means the app refuses to start if the mapped JPA entities don't match the
  actual schema — this turns a schema/entity mismatch into a startup failure instead of a
  runtime surprise.
- Integration tests run against a real PostgreSQL instance via Testcontainers rather than an
  in-memory substitute (e.g. H2), so Flyway migrations, Postgres-specific types (`UUID`,
  `TIMESTAMPTZ`), and constraints are exercised exactly as they'll behave in production.

## Security Impact

Constraints (uniqueness, not-null, foreign keys) are enforced by the database itself, as a
second line of defense behind application-level validation — an invariant like "email must be
unique" holds even if application code has a bug. Credentials are read from environment
variables (see `.env.example`), never committed.

## Operational Impact

One more stateful service to run locally (`docker compose up` starts PostgreSQL) and in any
deployment target. Migration history (`flyway_schema_history` table) gives a clear, ordered
record of every schema change ever applied to a given database, which matters for diagnosing
drift between environments.
