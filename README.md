# WorkSphere

Multi-tenant SaaS project-management platform (organizations, teams, projects, tasks, Kanban,
sprints, issues, comments, notifications, audit log, reporting, billing), built as a staged
learning project.

The full product/engineering specification lives in [`docs/WorkSphere-Specification.pdf`](docs/WorkSphere-Specification.pdf)
and is the source of truth for requirements. Project rules and working conventions for this
repo are in [`CLAUDE.md`](CLAUDE.md), which also holds the step-by-step progress checklist.

## Run it

Everything in containers, one command (needs Docker Desktop):

```bash
./scripts/init-env.sh                      # once: writes .env with strong random secrets
docker compose --profile app up --build    # builds and starts the database, Redis, Kafka, storage, mail, backend, frontend
```

Then open **http://localhost:8081** and sign in with the demo account
`demo.owner@worksphere.local` / `Demo-Passw0rd!` (a demo organization is created on an empty database).
Sent emails appear at http://localhost:8025. More options (infrastructure only, monitoring, resetting
data, troubleshooting) are in [`docs/operations/docker.md`](docs/operations/docker.md).

Running from your IDE instead: `docker compose up -d` for the infrastructure, then

```bash
cd backend  && ./mvnw spring-boot:run     # http://localhost:8080
cd frontend && npm install && npm run dev # http://localhost:5173
```

## Test it

```bash
cd backend  && ./mvnw verify   # unit + integration tests (Testcontainers: needs Docker running) + coverage gate
cd frontend && npm test
```

## Repository layout

```
backend/   src/main/java/com/worksphere/...   Spring Boot modular monolith (identity, organization, team,
                                              project, task, sprint, issue, notification, file, billing, ...)
frontend/  src/                               React + TypeScript + Vite
infra/     nginx/  observability/             reverse proxy config; Prometheus rules and Grafana dashboard
scripts/   init-env.sh                        generates local secrets
docs/      api/ operations/ security/ decisions/ (ADRs)
docker-compose.yml
```

## Documentation

- [Architecture decisions (ADRs)](docs/decisions/): why things are the way they are
- [Testing strategy](docs/security/testing-strategy.md)
- [Running with Docker](docs/operations/docker.md) and [observability runbook](docs/operations/observability.md)
- API notes: [real-time](docs/api/realtime.md), [billing](docs/api/billing.md)

## Prerequisites (to develop without Docker for the app itself)

Java 21, Node.js LTS, Docker Desktop (the tests and local infrastructure use it), Git.
