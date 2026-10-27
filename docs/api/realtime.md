# Real-time API (WebSocket / STOMP)

Endpoint: `ws://<host>/ws`. Send `Authorization: Bearer <access token>` as a **STOMP CONNECT header**.

| Destination | Who may subscribe | Event payload |
|---|---|---|
| `/user/queue/notifications` | the connected user (private) | `{type: "NOTIFICATION_CREATED", notificationId}` |
| `/topic/projects/{projectId}/board` | anyone who can see the project | `{type: "TASK_CREATED" \| "TASK_UPDATED" \| "TASK_MOVED" \| "TASK_DELETED", projectId, taskId}` |

Rules: clients cannot `SEND`; a forbidden `SUBSCRIBE` or a bad token closes the connection with an
ERROR frame. Payloads are ids only; fetch the data with the normal REST endpoints.
See ADR-006.
