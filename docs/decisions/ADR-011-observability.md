# ADR-011: Observability with Actuator, Micrometer / Prometheus and JSON logs

## Context
Spec section 25 asks for structured JSON logs, request/correlation ids, health and readiness endpoints,
application, database, Kafka-lag and Redis metrics, error-rate and latency dashboards, and alerting, so a
student learns to diagnose an incident from logs and metrics rather than restarting the server.

## Decision
- **Spring Boot Actuator + Micrometer** with the Prometheus registry. Exposed: `health`, `info`,
  `prometheus` only.
- **Health groups:** liveness = process state only. Readiness = process state + **database**. Redis, Kafka,
  mail and storage are excluded on purpose: the app is designed to degrade without them (ADR-007, 008,
  009), so a Redis outage must not pull every instance out of rotation. They are made visible by metrics
  and alerts instead. Anonymous health shows status only, no component details.
- **Metrics access:** health probes and `info` are public; every other actuator path needs a Bearer scrape
  token (`METRICS_TOKEN`) checked in constant time, and is closed when no token is configured
  (`MetricsAccessFilter`). Operators should also block `/actuator/**` at the proxy.
- **Custom metrics** for what generic ones miss: outbox backlog and age, publish counts and failures,
  Redis reachability (`worksphere_redis_up`), rate-limit rejections by scope, webhook outcomes. Kafka
  consumer lag comes from the Kafka client's own metrics (Micrometer binding).
- **Logs:** text in development, JSON (logstash encoder) with profile `prod`; `requestId` on every line.
  `RequestLoggingFilter` writes one access line per request (path only, never query strings or headers;
  5xx as WARN). Health probes and metric scrapes are not logged.
- **Correlation id hardening:** a client-supplied `X-Request-Id` is copied into every log line, so it is
  accepted only if it is a short plain identifier; otherwise a fresh UUID is used (no log injection).
- **Alerts and dashboard as code** in `infra/observability/` (Prometheus rules, Grafana provisioning,
  dashboard JSON) and an optional compose profile `observability`.
- **Bug found and fixed on the way:** unknown URLs, wrong HTTP methods and missing request parts were
  reported as 500 INTERNAL_ERROR by the catch-all handler. They are now 404 / 405 / 415 / 406 / 400 with
  stable codes, which also keeps false 5xx out of the error-rate alert.

## Alternatives
- OpenTelemetry tracing and OTLP export: valuable for multi-service systems; deferred until services are
  split (step 29). Correlation ids and metrics cover the monolith.
- A separate management port: cleaner network isolation but needs infrastructure support to keep it
  private and is harder to test; a token plus proxy rule is simpler for the learning deployment.
- Making Redis/Kafka part of readiness: would turn a degraded-but-working state into a full outage.

## Consequences
- Alert delivery (Alertmanager) is not included; the rules define when to alert.
- Gauges that query the database (outbox) are cached for 5 seconds to protect it from frequent scrapes.
- Percentile histograms increase the number of time series for `http.server.requests`; acceptable at this scale.

## Security impact
No metrics or health details for anonymous users, constant-time token comparison, no query strings or
secrets in access logs, no log injection through `X-Request-Id`, no stack traces in API responses.

## Operational impact
New settings: `METRICS_TOKEN`, `SPRING_PROFILES_ACTIVE=prod`, `FORWARD_HEADERS_STRATEGY`. See
`docs/operations/observability.md` for the runbook.
