# ADR-007: Redis for rate limiting and caching (fail-open)

## Context
Spec section 17 lists Redis for caching a project summary, rate limiting (login/API limits) and
short-lived state. Section 29 names brute force and expensive-call DoS as threats.

## Decision
- **Rate limiting** (`ApiRateLimitFilter`): fixed one-minute window counters in Redis.
  Signed-in callers are counted by user id, anonymous callers by IP, and `/api/v1/auth/**` has a
  much lower limit (default 30/min vs 300/min). Over the limit: HTTP 429, code `RATE_LIMITED`,
  `Retry-After` header.
- **Login lockout** (`RedisLoginAttemptGuard`, plugged into the existing `LoginAttemptGuard` seam):
  5 wrong passwords for one email locks sign-in for 15 minutes; a success resets the count. The
  email is SHA-256 hashed in the key.
- **Cache** (`TaskSummaryCache`): per-column task counts for a project, 60 s TTL. Authorization is
  checked before the cache is read, and entries are evicted after the transaction commits.
- **Counters are atomic**: increment and expiry run in one Lua script, so a crash cannot leave a
  counter that never expires.
- **Fail-open**: if Redis is down every Redis call logs a warning and the app carries on
  (no limiting, cache misses). Short timeouts (300 ms) stop a dead Redis from slowing requests.

## Alternatives
- In-memory limits (Bucket4j/Caffeine): simpler, but each backend instance would count separately.
- Fail-closed (reject requests if Redis is down): safer against abuse, but one cache outage would
  become a full outage. Chosen against for this system; revisit for the auth endpoints if abuse is
  seen in production.
- Spring Cache annotations: less code, but eviction-after-commit and the authorization-first rule
  are easier to see and test in explicit code.

## Consequences
- Fixed windows allow a burst of up to 2x the limit across a window boundary. Acceptable here.
- A Redis outage disables brute-force protection until it recovers (visible in the logs; the
  observability step adds a Redis health indicator and alert).
- Redis keeps no data on disk; everything in it can be rebuilt or is safe to lose.

## Security impact
Mitigates credential guessing, sign-up abuse and request floods. Cached data never bypasses the
authorization check. No personal data in keys (hashed email, user id, IP only).

## Operational impact
New dependency `redis:7` (in `docker-compose.yml`), `REDIS_HOST`/`REDIS_PORT` settings. Limits are
configurable under `app.rate-limit.*`. Behind a reverse proxy the real client IP must be forwarded.
