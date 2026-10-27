# ADR-012: Container images and the local environment

## Context
Spec section 26 wants one command to start the local infrastructure, externalised configuration, no
committed secrets, predictable database migrations and demo seed data, with services frontend, backend,
PostgreSQL, Redis, Kafka, object storage and a mail catcher.

## Decision
- **Images:** the backend is built in two stages (JDK builds the jar with the Maven wrapper, then a JRE-only
  runtime image); the frontend is built with Node and served by `nginx-unprivileged`. Both run as non-root users.
  The backend sizes its heap from the container memory limit and exits on OutOfMemoryError so the orchestrator
  restarts it cleanly.
- **One compose file, profiles:** the infrastructure always starts; `app` adds backend and frontend;
  `observability` adds Prometheus and Grafana. Developers can run the app from the IDE against the same
  infrastructure.
- **nginx is the single entry point** for the browser: static files, `/api` and `/ws` proxied, operational
  endpoints hidden, security headers (CSP, frame, nosniff, referrer, permissions) set in one place. Same origin
  means no CORS in this setup; the allowed origins stay configurable for other setups.
- **Configuration from the environment.** `.env` (git-ignored) is generated with random secrets by
  `scripts/init-env.sh`. The `:-` defaults in compose are development values; the backend refuses to start with
  them when `APP_ENVIRONMENT=production` (`ProductionConfigGuard` checks the JWT and webhook secrets, metrics
  token, database and storage passwords, and that demo data is off). The error names the problem, never the value.
- **Migrations** are Flyway, run by the backend at startup, so starting the container applies the schema.
- **Demo data** (`DemoDataSeeder`) goes through the real services, only when `SEED_DEMO_DATA=true`, only on an
  empty database, and never in production.
- **Health ordering** uses real health checks (`depends_on: service_healthy`); the backend's own check uses bash's
  `/dev/tcp` because the runtime image has no curl.
- **Network exposure:** infrastructure ports bind to 127.0.0.1.

## Alternatives
- Spring Boot buildpacks / Jib instead of a Dockerfile: fewer lines, but the Dockerfile is the thing a student
  should learn to read and it needs no extra tooling.
- A separate compose file per environment: more duplication; profiles keep one source of truth.
- Distroless runtime: smaller attack surface, but no shell for the health check or for debugging.

## Consequences
- The first backend build downloads dependencies (slow, cached afterwards). A flaky network can fail one attempt;
  retry settings and the cache make a re-run cheap.
- A PostgreSQL volume created with an old password keeps it; documented in `docs/operations/docker.md`.
- Single-node Kafka and the legacy MinIO image are for local use only.

## Security impact
Non-root containers, no secrets in images or Git (`.dockerignore` excludes `.env`), loopback-only infra ports,
CSP and other headers at the edge, `/actuator` unreachable from outside, startup refusal for unsafe production
configuration.

## Operational impact
New files: `backend/Dockerfile`, `frontend/Dockerfile`, `infra/nginx/default.conf`, `docker-compose.yml`,
`scripts/init-env.sh`. New settings: `APP_ENVIRONMENT`, `SEED_DEMO_DATA`, `CORS_ALLOWED_ORIGINS`, `PUBLIC_URL`,
`FORWARD_HEADERS_STRATEGY`.
