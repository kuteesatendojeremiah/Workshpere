# Running WorkSphere with Docker

## The three ways to start it

| Goal | Command | What runs |
|---|---|---|
| Develop in your IDE | `docker compose up -d` | PostgreSQL, Redis, Kafka, object storage (MinIO), mail catcher (Mailpit) |
| Run the whole app | `docker compose --profile app up --build` | the above + backend + frontend (nginx) |
| Add monitoring | `docker compose --profile app --profile observability up --build` | + Prometheus and Grafana (see the observability runbook) |

First time: `./scripts/init-env.sh` creates `.env` with random secrets (it never overwrites an existing
`.env`). Without a `.env`, the development defaults are used, which is fine on your own machine only.

## Where things are

| What | Address |
|---|---|
| The application (nginx, serves the pages and proxies `/api` and `/ws`) | http://localhost:8081 |
| Backend directly (debugging) | http://localhost:8080 (bound to 127.0.0.1) |
| Emails the app sent | http://localhost:8025 (Mailpit) |
| Storage console | http://localhost:9001 (credentials: `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY`) |
| Prometheus / Grafana | http://localhost:9090 / http://localhost:3000 |

All infrastructure ports are published on `127.0.0.1` only, so other machines on your network cannot reach
your database.

## Demo data

With `SEED_DEMO_DATA=true` (the default in compose) an EMPTY database gets an organization "Acme Demo" with two
projects, tasks in every Kanban column and a sprint. Sign in as `demo.owner@worksphere.local` with password
`Demo-Passw0rd!`. It is skipped if that user already exists, and the backend refuses to start with
`APP_ENVIRONMENT=production` while demo seeding is on, because that password is public.

## How the pieces fit

- **nginx** (frontend container, non-root) is the only thing the browser talks to: static files, `/api/` and `/ws`
  go to the backend, `/actuator` and `/health` return 404 from outside. It adds the security headers
  (CSP, `X-Frame-Options`, `nosniff`, ...). One address means no CORS.
- **backend** image is built in two stages (Maven build, then a JRE-only runtime) and runs as a non-root user with
  the heap sized from the container's memory limit. Database migrations (Flyway) run automatically at startup, so
  starting the container is the whole deployment procedure for the schema.
- **Health ordering:** the backend waits for PostgreSQL, Redis, Kafka and storage to be healthy; the frontend waits
  for the backend's readiness probe. Mailpit is not waited for (the app works without email).
- **Kafka has two listeners:** `kafka:29092` for other containers and `localhost:9092` for your IDE.

## Everyday commands

```bash
docker compose --profile app logs -f backend     # JSON log lines; each has a requestId
docker compose --profile app ps                  # health of each service
docker compose --profile app down                # stop; your data volumes are kept
docker compose --profile app up --build -d       # rebuild after changing code
```

## Troubleshooting

- **Backend restarts with "password authentication failed for user worksphere".** PostgreSQL only applies
  `DB_PASSWORD` the *first* time it creates its data volume. If you created the volume earlier with a different
  password, the new `.env` does not match it. Either put the old password back in `.env`, or, if you do not need
  the data, delete the volume with `docker compose down -v` (**this erases the database and stored files**).
- **"port is already allocated".** Something else uses 5432, 6379, 9092, 9000, 8080 or 8081; stop it or change the
  published port in `docker-compose.yml`.
- **Dependency download fails while building the backend image.** A flaky network; run the build again (the Maven
  cache is kept between attempts).
- **Tests complain about Docker.** The backend tests start their own throwaway containers (Testcontainers) and need
  Docker Desktop running; they do not use these compose containers.

## Not for production as is

This compose file is a local environment: single-node Kafka, a legacy MinIO image, development defaults for
secrets, no TLS. Deployment (step 27) builds on the same images with real secrets, `APP_ENVIRONMENT=production`
(which makes the backend refuse unsafe settings) and TLS in front.
