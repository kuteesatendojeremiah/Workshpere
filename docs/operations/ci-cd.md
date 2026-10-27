# CI/CD

How a change gets from a branch to a released, versioned artifact, and how to undo a release (spec section 27).
Design reasons are in ADR-013.

## The flow

```
branch -> pull request -> CI must pass + 1 review -> merge to main -> (tag vX.Y.Z) -> Release workflow -> images in the registry
```

1. Work on a branch; open a **pull request** (the template lists the Definition of Done).
2. **CI** (`.github/workflows/ci.yml`) runs on every pull request and every push to `main`:

   | Job | What it proves |
   |---|---|
   | `backend` | `./mvnw verify`: unit tests, all Testcontainers integration tests (PostgreSQL, Redis, Kafka, MinIO, Mailpit), the JaCoCo coverage gate |
   | `frontend` | lockfile install, lint, type-check + production build, tests, `npm audit` of production dependencies |
   | `images` | the compose file is valid and both Dockerfiles build on a clean Linux machine |
   | `secrets-scan` | no committed secret anywhere in the Git history (gitleaks) |
   | `dependency-review` | (pull requests) the change does not add a dependency with a known high-severity vulnerability |

3. A reviewer approves; **merge** (linear history, so `main` reads as a list of reviewed changes).
4. To release, tag a commit on `main`: `git tag v1.4.0 && git push origin v1.4.0`.
5. The **Release** workflow (`release.yml`) re-runs the same CI, then publishes
   `ghcr.io/<owner>/worksphere-backend:1.4.0` and `...-frontend:1.4.0` (also tagged with the full commit hash),
   with provenance and an SBOM attached, and creates the GitHub Release with generated notes.

Nothing is published unless every check passed, and an image tag is never reused, so `1.4.0` always means the
same bytes.

## Protecting `main` (do this once)

Branch protection is a setting on GitHub, not a file in the repository, and it changes how you push, so it is
a script you run deliberately:

```bash
gh auth login               # needs admin rights on the repository
./scripts/protect-main.sh   # or: ./scripts/protect-main.sh owner/repo
```

It requires pull requests with one approving review (including code owners), up-to-date branches, all five CI
checks, resolved conversations and linear history, and forbids force-pushes and deletion, including for admins.
Afterwards `git push origin main` is rejected: push a branch and open a pull request. Until it is on, nothing
stops a direct push, so enable it before real work starts.

## Supply-chain safety

- Every third-party action is pinned to a **commit hash**, not a tag (tags can be moved). Dependabot opens pull
  requests to move the pins forward, and they go through the same CI.
- Workflows get a read-only token by default; only the release job can write packages and releases.
- Dependabot also watches Maven, npm and Docker base images weekly.
- Secrets are never in the repository (gitleaks enforces it) and not in the images (`.dockerignore`).

## Rolling back

The aim is that going back is boring: **deploy the previous version.**

1. **Application:** every release is an immutable tag. Roll back by deploying the previous tag (for example
   `1.3.2`) instead of `1.4.0`. No rebuild, so no surprises; check `/actuator/health/readiness` and the dashboard
   afterwards (see the observability runbook).
2. **Database: migrations only go forward, so they must be safe for the OLD code.** Flyway never runs backwards.
   To keep rollback possible, a migration may only be *additive* in the release that introduces it (new table,
   new nullable column, new index). Use *expand, then contract* across two releases for anything else:
   - Release N: add the new column, write to both, read the old one.
   - Release N+1: switch reads to the new column.
   - Release N+2: drop the old column, once no running version uses it.
   A destructive migration (drop, rename, `NOT NULL` on existing data) ships alone, in a later release, after a
   backup, never in the same release as the code change that needs it.
3. **Backups:** take a database backup (or snapshot) immediately before any release that contains a migration.
   Restoring it is the last resort and loses data written since, so it is for corruption, not for ordinary
   rollbacks.
4. **Events:** Kafka consumers are idempotent and the outbox keeps unpublished events, so re-deploying an older
   version does not lose or duplicate business effects.
5. **Configuration and secrets** are versioned with the deployment target, not with the image; a bad config
   change is rolled back by restoring the previous values and restarting.
6. **Flags:** risky features should ship disabled and be switched on separately from the deployment.

## Environments and deployment

CI and the release are in place. Environment-specific deployment (staging, then production with a manual
approval) is added with step 27 (cloud deployment) as a `deploy` workflow that takes an environment and an image
tag, so a rollback is the same workflow run with the older tag.
