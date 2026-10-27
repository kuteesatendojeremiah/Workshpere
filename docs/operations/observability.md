# Observability runbook

How to see what WorkSphere is doing, and how to diagnose a problem from logs and metrics instead of just
restarting it (spec section 25). Design reasons are in ADR-011.

## What exists

| Signal | Where | Notes |
|---|---|---|
| Liveness | `GET /actuator/health/liveness` | Public. "Is the process alive?" Never depends on other systems |
| Readiness | `GET /actuator/health/readiness` | Public. Depends on the **database only**. Redis, Kafka, mail and storage are *not* in it: the app degrades without them instead of being pulled out of rotation |
| Overall health | `GET /actuator/health` | Public, status only. Includes db, redis, outbox (DOWN when events are stuck > 5 min) |
| Metrics | `GET /actuator/prometheus` | Needs `Authorization: Bearer $METRICS_TOKEN`; closed if the token is empty. Keep `/actuator/**` off the public internet at the reverse proxy as well |
| Logs | stdout | Text in development; one JSON object per line with profile `prod`. Every line has `requestId` |
| Request id | `X-Request-Id` header | Echoed in every response and in every log line and audit entry. A client-supplied id is only accepted if it is 1-64 chars of `A-Za-z0-9._-` |
| Dashboards | Grafana "WorkSphere overview" | `docker compose --profile observability up -d`, then http://localhost:3000 |
| Alerts | `infra/observability/alert-rules.yml` | 11 rules; each says what it means and where to look |

Start it: put the token in `infra/observability/metrics-token` (same value as `METRICS_TOKEN`), then
`docker compose --profile observability up -d`. Prometheus: http://localhost:9090.

## Metrics worth knowing

- `http_server_requests_seconds_*`: rate, errors (`status` tag) and latency (histogram, so p95/p99).
- `hikaricp_connections_*`: database pool (`pending > 0` = requests waiting for a connection).
- `worksphere_outbox_unpublished`, `worksphere_outbox_oldest_age_seconds`: events saved but not yet in Kafka.
- `worksphere_outbox_published_total`, `worksphere_outbox_publish_failures_total`.
- `kafka_consumer_fetch_manager_records_lag_max`: consumer lag (from the Kafka client).
- `worksphere_redis_up`: 1 when Redis answers PING. Redis down means no rate limiting or caching, silently.
- `worksphere_ratelimit_rejected_total{scope}`, `worksphere_billing_webhook_total{outcome}`.

## Diagnosing: a worked method

1. **Is it down or slow or wrong?** Dashboard: request rate, 5xx share, p95 latency.
2. **What changed?** Look for the first minute the graph moved; compare with deploys.
3. **Which dependency?** Database pool (pending connections), Redis up, outbox age, Kafka lag.
4. **Find a concrete failing request.** Logs, level WARN, logger `http.access` (500s are logged as WARN;
   each line has method, path, status, duration and `requestId`). Take one `requestId` and read every
   line with it: the stack trace is there (stack traces are never returned to API clients).
5. **Check data, not guesses.** Audit log (`GET /organizations/{id}/audit-logs`) for what the user did;
   `payment_events.outcome` for webhooks; `outbox_events WHERE published_at IS NULL` for stuck events.
6. Only then fix or restart. Restarting a pod with a stuck outbox or a full connection pool fixes nothing.

## Playbooks for the alerts

- **BackendDown**: process/container health first (`liveness`), then the last log lines before it stopped.
- **HighServerErrorRate**: step 4 above. A single failing endpoint usually means a bug; all endpoints
  means a dependency (database).
- **DatabasePoolSaturated**: find long transactions (`pg_stat_activity`), slow queries, or a leak; raising the
  pool size only hides it.
- **RedisUnreachable**: the API still works but login lockout and rate limits are OFF. Restore Redis; consider
  temporary stricter limits at the proxy.
- **OutboxBacklogStuck / OutboxPublishFailures**: Kafka reachable? Look for "Could not publish outbox event"
  warnings. No event is lost; they wait in `outbox_events` and flow in order when Kafka returns.
- **KafkaConsumerLagging**: a slow consumer, or poison messages. Look at the dead-letter topic
  `worksphere.events.DLT`.
- **WebhookSignatureRejections**: probing, or `WEBHOOK_SECRET` differs from the provider's.

## Production checklist

- `SPRING_PROFILES_ACTIVE=prod` (JSON logs).
- `METRICS_TOKEN` set to a long random value; `/actuator/**` blocked at the reverse proxy from the internet.
- `FORWARD_HEADERS_STRATEGY=framework` behind a proxy, so rate limits and logs use the real client address.
- Alert delivery (Alertmanager to email / chat / pager) is configured outside this repository; the rules
  here only define *when* to alert.
