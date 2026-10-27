# ADR-010: Billing, plan limits and idempotent payment webhooks

## Context
Spec section 20 asks for the plans Free / Professional / Enterprise, plan feature limits, a
subscription lifecycle, payment creation, webhook verification, idempotent webhook processing, grace
periods and cancellation, and billing history. Section 24 requires that a duplicate payment webhook
does not duplicate subscription state, and section 29 names replay as a threat.

## Decision
- **Data** (V35): `plans` (seeded), `subscriptions` (one row per organization that ever left Free),
  `payment_events` (every accepted webhook, primary key = the provider's event id), `billing_history`
  (append-only payments). New permission `MANAGE_BILLING`, held by `ORGANIZATION_OWNER`.
- **Limits:** Free = 3 active projects / 5 members, Professional = 50 / 50, Enterprise = unlimited.
  Enforced when a project is created and when someone is invited (open invitations count). Limits only
  block NEW things; nothing is ever deleted on a downgrade.
- **Choosing a paid plan only starts a payment.** The plan changes when the provider's
  `payment.succeeded` webhook arrives, never because a client says it paid. The payment amount must
  equal the plan price or the event is rejected.
- **Which plan is in force is computed** from stored state and the clock (`Subscription.effectivePlanKey`):
  after a failed payment the paid plan keeps working for a 7-day grace period; cancellation takes effect
  at the end of the paid period. No scheduled job is needed and the rules are unit-tested as plain
  functions of time.
- **Webhook security:** `POST /api/v1/webhooks/payment` needs no user token. It requires an HMAC-SHA256
  signature over `"<timestamp>.<raw body>"` (`X-Webhook-Signature: t=...,v1=...`), verified in constant
  time against the raw body before parsing; timestamps older (or newer) than 5 minutes are refused to
  stop replay. Every failure answers the same 401, with no hint why.
- **Idempotency:** the first statement of processing is
  `INSERT INTO payment_events ... ON CONFLICT (provider_event_id) DO NOTHING`. Zero rows inserted means
  the event was already handled, so nothing else happens and we answer 200 `DUPLICATE` (the provider must
  stop retrying). The insert and the state change are one transaction: if processing fails, the claim
  rolls back too and the provider's retry is processed normally.
- **Provider abstraction:** `PaymentGateway` creates the payment reference. `SimulatedPaymentGateway`
  invents one; a real provider (Stripe, Paddle) replaces it and its event format is mapped onto
  `PaymentWebhookEvent`. No payment card data ever touches this system.
- Events: `SUBSCRIPTION_UPDATED` goes through the outbox (ADR-008); every change is audited.

## Alternatives
- Trust a client "payment succeeded" call: trivially forgeable. Rejected.
- Check "already processed" with a SELECT before processing: two simultaneous deliveries could both pass
  the check. The primary-key insert is atomic.
- A scheduler that downgrades expired subscriptions: more moving parts and a window where the stored
  state is wrong. Computing from the clock has none.
- Org id in the body of `POST /subscriptions` as the spec's path suggests: the org is in the URL
  instead (`/organizations/{id}/subscription`) like every other tenant-owned resource, and membership
  plus `MANAGE_BILLING` are verified server-side. This is a deliberate small deviation from the path
  in spec section 12.

## Consequences
- With the simulated gateway nobody actually pays; `docs/api/billing.md` shows how to send a signed
  webhook by hand.
- A single shared webhook secret (`WEBHOOK_SECRET`); rotating it needs a short overlap period if the
  provider supports it.
- Billing history stores amounts and plan names only, no payment instrument data.

## Security impact
Forged, tampered and replayed webhooks are rejected and change nothing (tested); a wrong amount does not
unlock a plan; billing endpoints are tenant-scoped and permission-checked; the webhook endpoint is rate
limited like every other `/api` route.

## Operational impact
New setting `WEBHOOK_SECRET` (must be set to a strong random value outside development). Watch
`payment_events.outcome` for `REJECTED_*` and `IGNORED_*` values, and the 401 rate on the webhook path.
