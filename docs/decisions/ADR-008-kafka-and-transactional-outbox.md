# ADR-008: Kafka events through a transactional outbox

## Context
Spec section 16 lists the domain events (USER_REGISTERED, TASK_CREATED, ...) and says the design must
decide explicitly how duplicates, retries and failures are handled instead of assuming that "Kafka
means exactly once". It also asks for a transactional outbox so that "the database committed but the
event was lost" cannot happen.

## Decision
- **Outbox table** (`outbox_events`, migration V32). Services call `OutboxService.record(...)` inside
  their own transaction. The method is `Propagation.MANDATORY`: outside a transaction it refuses to
  run, so an event can never be written without its business change, and a rolled-back change takes its
  event with it.
- **Relay** (`OutboxRelay`): a scheduled job (every second) locks a batch of unpublished rows with
  `FOR UPDATE SKIP LOCKED`, sends them to topic `worksphere.events` keyed by aggregate id, and marks
  them published. It stops at the first failure to keep order. Published rows are deleted after 7 days.
- **Delivery is at-least-once.** The relay can send an event twice (crash after Kafka accepted it but
  before the row was marked). The producer uses `acks=all` and idempotence to avoid duplicates from
  retries inside one producer session.
- **Consumers must be idempotent.** `ActivityFeedConsumer` (reporting module) inserts into
  `activity_feed` with a UNIQUE `event_id` and `ON CONFLICT DO NOTHING`, so handling an event twice
  equals handling it once.
- **Failures:** a record the consumer cannot process is retried 3 times (1 s apart), then published to
  `worksphere.events.DLT`. A malformed (non-JSON) message skips retries. One bad message never blocks
  the partition behind it.
- **Event content:** an envelope `{eventId, eventType, aggregateType, aggregateId, organizationId,
  occurredAt, data}` with ids and a few safe facts only. No emails, tokens or passwords.
- Events wired now: USER_REGISTERED, ORGANIZATION_CREATED, MEMBER_INVITED, PROJECT_CREATED,
  TASK_CREATED, TASK_ASSIGNED, TASK_STATUS_CHANGED, COMMENT_CREATED, ISSUE_CREATED, SPRINT_STARTED,
  SPRINT_COMPLETED. SUBSCRIPTION_UPDATED arrives with billing (step 23).

## Alternatives
- Publish to Kafka directly inside the request: loses events when the database commits but Kafka is
  down (or the reverse: publishes events for changes that later roll back).
- Kafka transactions / exactly-once: cannot span PostgreSQL and Kafka anyway, and add complexity.
- CDC (Debezium) reading the outbox table: more robust at scale, but adds another moving part; the
  polling relay is simpler to learn and operate. The table design allows switching later.

## Consequences
- Events are delivered a moment after the commit (about a second), not synchronously.
- If Kafka is down the API keeps working; events accumulate in `outbox_events` and flow when Kafka
  returns. A growing count of unpublished rows is the signal to alert on (step 24).
- Ordering is guaranteed per aggregate id (same partition), not across aggregates.
- The `payload` column is TEXT (the exact bytes sent); we do not query inside it.

## Security impact
Events leave the database, so they contain ids only. Consumers authorize nothing by themselves; any
user-facing read built on events must still apply tenant checks (`organizationId` is in every event).

## Operational impact
New dependency: Kafka (single-node KRaft in `docker-compose.yml`, `KAFKA_BOOTSTRAP_SERVERS`). Topics
are created at startup. Watch: unpublished outbox rows, consumer lag, DLT depth.
