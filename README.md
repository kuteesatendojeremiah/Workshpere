# WorkSphere

Multi-tenant SaaS project-management platform (organizations, teams, projects, tasks, Kanban,
sprints, issues, comments, notifications, audit log, reporting, billing), built as a staged
learning project.

The full product/engineering specification lives in [`docs/WorkSphere-Specification.pdf`](docs/WorkSphere-Specification.pdf)
and is the source of truth for requirements. Project rules and working conventions for this
repo are in [`CLAUDE.md`](CLAUDE.md).

## Status

Early scaffolding stage (Step 1 of `build-prompts.md`). No application code yet.

## Repository layout

```
backend/   src/main/java/com/worksphere/...   (Spring Boot)
frontend/  src/  tests/                        (React + TypeScript)
infra/     docker/  nginx/
docs/      architecture/ api/ database/ security/ decisions/ (ADRs)
.github/workflows/
docker-compose.yml
```

## Prerequisites

- Java 21
- Maven (or the project's Maven wrapper, once added)
- Node.js LTS
- Docker Desktop
- Git

## Setup

1. Clone this repository.
2. Copy `.env.example` to `.env` and fill in real values.
3. Backend and frontend setup instructions will be added as those modules are scaffolded
   (see `build-prompts.md` Steps 2 and 17).

## Architecture decisions

See [`docs/decisions/`](docs/decisions/) for ADRs recording significant architectural choices.
