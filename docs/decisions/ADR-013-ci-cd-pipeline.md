# ADR-013: CI/CD with GitHub Actions, immutable versioned images and forward-only migrations

## Context
Spec section 27 asks for a protected main branch, pull requests for production changes, automated tests,
versioned artifacts, environment-specific deployment and a rollback strategy. Section 29 names supply-chain
and secret leakage as threats.

## Decision
- **GitHub Actions**, since the code is on GitHub. One reusable CI workflow (`backend`, `frontend`, `images`,
  `secrets-scan`, `dependency-review`) runs for pull requests and pushes, and the release workflow calls the
  same workflow, so a release cannot skip a check.
- **Release by tag** (`vX.Y.Z`): publishes backend and frontend images to the GitHub Container Registry tagged with
  the semantic version and the commit hash, with provenance and an SBOM, plus a GitHub Release. Tags are never
  moved or reused.
- **Third-party actions pinned to commit hashes**, kept current by Dependabot (which also covers Maven, npm and
  base images). Workflows default to a read-only token; write permissions are granted per job.
- **Secret scanning** with the gitleaks container (not the licensed action) over the whole history; production
  npm dependencies are audited and block, dev-only tooling is reported by Dependabot.
- **Branch protection** is provided as a script (`scripts/protect-main.sh`) rather than applied automatically:
  it changes repository settings and the way the owner pushes, so the owner runs it deliberately.
- **Rollback = deploy the previous immutable tag.** Database migrations are forward-only and must be
  backward compatible with the previous release (expand / migrate / contract; destructive changes ship alone,
  after a backup), so an application rollback never requires a database rollback.
- Fixed on the way: `mvnw` and `init-env.sh` were stored non-executable (a Windows artefact), which would have
  failed on any Linux runner or Linux Docker build. And 3 high-severity npm advisories in dev-only tooling
  (Playwright, undici) were fixed with a lockfile update.

## Alternatives
- Pinning actions by tag: easier to read, but a repointed tag runs attacker code in the pipeline.
- Automatic deployment of every merge to production: faster, but removes the human check; deployment is a
  separate, approved step (step 27).
- Rolling back databases with down-migrations: Flyway's undo is paid and untested in practice; additive
  migrations avoid the need.
- Building images only at deploy time: no artifact to name, test or roll back to.

## Consequences
- The pipeline has not run on GitHub yet when this is written (it needs a push); the first run may expose
  environment differences, to be fixed in a follow-up.
- Backend CI is slow (about 10 minutes) because it starts five containers per test run.
- The expand/contract discipline costs extra releases for destructive schema changes; that is the price of
  safe rollback.

## Security impact
Least-privilege tokens, pinned actions, secret scanning of history, dependency review on pull requests,
provenance and SBOM for released images, protected `main` with required review and checks.

## Operational impact
New files under `.github/` (workflows, Dependabot, CODEOWNERS, PR template), `scripts/protect-main.sh`,
`docs/operations/ci-cd.md`. One-time action for the owner: run `scripts/protect-main.sh`.
