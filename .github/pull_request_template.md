## What and why
<!-- What does this change, and why? Link the step in CLAUDE.md / the spec section. -->

## Definition of done (CLAUDE.md)
- [ ] Database changes have a **new** Flyway migration (no edits to applied ones), safe to run before the new code deploys
- [ ] API has validation and server-side authorization; tenant checks are covered
- [ ] Unit tests, and an integration test with Testcontainers where it touches the database
- [ ] Negative security tests (other organization, missing permission, bad token) where relevant
- [ ] Frontend handles loading, empty, error and success states
- [ ] Audit / notification / event behaviour is defined where relevant
- [ ] Docs / ADR updated; no secrets or personal data in code, logs or fixtures

## Rollback
<!-- If this goes wrong in production, is "deploy the previous version" enough? If it needs more (a data fix, a flag), say so. -->
