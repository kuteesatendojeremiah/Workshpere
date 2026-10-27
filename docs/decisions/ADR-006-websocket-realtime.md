# ADR-006: Real-time updates over STOMP/WebSocket

## Context
Spec section 15 asks for WebSockets for user-visible low-latency events (notifications, task-board
changes, mentions) and says ordinary CRUD should stay on REST.

## Decision
- STOMP over WebSocket at `/ws`, using Spring's in-memory simple broker.
- The JWT is sent in the STOMP `CONNECT` frame (browsers cannot set headers on a WebSocket) and
  verified by `StompAuthChannelInterceptor`. No valid token, no connection.
- `SUBSCRIBE` is allow-listed. A user's own `/user/queue/notifications` is always allowed.
  Anything else needs a `RealtimeTopicAuthorizer` that claims the destination and approves it;
  the project module approves `/topic/projects/{id}/board` only for people who can see the project.
- Clients may not `SEND` anything. All writes go through REST.
- Events carry **ids only** (type, projectId, taskId, notificationId). The browser re-fetches over
  REST, so authorization and tenant checks stay in exactly one place and a socket message can
  never leak data.
- Events are published **after the transaction commits** (`RealtimePublisher`), so a rolled-back
  change never appears on a screen. A failed push is logged and swallowed; REST is the truth.

## Alternatives
- Server-Sent Events: simpler, one-way, but we want a standard subscription model and a path to a
  relay broker. WebSocket/STOMP gives that.
- Pushing full objects in events: fewer round trips, but duplicates authorization logic and risks
  leaking fields.
- Polling: simplest, but wasteful and slow to feel "live".

## Consequences
- The simple broker is in memory, so it only works with ONE backend instance. Running several
  instances (later phases) needs a relay broker (Redis pub/sub, RabbitMQ or Kafka-backed fan-out).
- Subscription authorization is checked once at subscribe time. If someone is removed from a
  project while connected, they keep receiving id-only events until they disconnect; they still
  cannot read any data because REST re-checks every time.

## Security impact
Token validated at connect; per-destination subscribe authorization; send disabled; no data in
payloads. Tested in `WebSocketIntegrationTest` (no token, outsider subscribe, notification privacy,
send refused).

## Operational impact
Reverse proxies must pass the `Upgrade` header for `/ws`. Origins are limited by
`app.cors.allowed-origins`.
