# ADR-005: Strict validation of pagination, sort and filter parameters

## Context

Step 18 (testing hardening) added the spec 24.1 test "invalid pagination/filter values are
rejected safely". Writing it exposed real defects: `?status=NOT_A_STATUS`, `?assigneeId=not-a-uuid`,
a non-UUID path id and `?sort=unknownField` all returned **500** (the catch-all handler), while
`?page=abc` and `?size=lots` silently returned **200** with defaults, and `?size=100000` was
honoured, allowing one request to pull an unbounded result set.

## Decision

- `spring.data.web.pageable.max-page-size: 200` (default page size 20). Oversized `size` values
  are clamped, not rejected, so the response is always bounded. 200 is deliberate: the Kanban
  board requests 200 tasks per column.
- `PaginationParameterInterceptor` (on `/api/**`) rejects a non-numeric or out-of-range `page`
  (< 0) / `size` (< 1) with 400 `VALIDATION_ERROR`, instead of Spring's silent fallback.
- `GlobalExceptionHandler` maps `MethodArgumentTypeMismatchException` (bad enum / UUID in query or
  path) and `PropertyReferenceException` (unknown sort property) to 400 `VALIDATION_ERROR`.
  Messages name the offending parameter but never echo the raw value or exception text.

## Alternatives

- Reject oversized `size` with 400 instead of clamping: stricter, but breaks well-meaning clients
  for no security gain. Clamping bounds the cost just as well.
- Bean Validation on every controller `Pageable`: not supported directly for `Pageable`; would
  need a custom argument resolver per controller. A single interceptor is simpler.

## Consequences

Clients get one consistent 400 shape for every bad list/filter input. A client that previously
relied on `page=abc` "working" will now fail loudly - which is the point.

## Security impact

Removes an unbounded-read vector (resource exhaustion) and stops 500s that log stack traces for
attacker-controlled input. Sorting by arbitrary properties is no longer a probe for entity
field names (error is generic 400).

## Operational impact

Fewer spurious 500s in error-rate alerts. `max-page-size` can be tuned in `application.yml`.
