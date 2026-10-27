# Billing API

All organization routes need a bearer token. Changing the plan, cancelling and reading billing history
need the `MANAGE_BILLING` permission (organization owners); reading the current subscription needs only
membership.

| Method and path | Purpose |
|---|---|
| `GET /api/v1/plans` | The three plans with price and limits (`null` = unlimited) |
| `GET /api/v1/organizations/{id}/subscription` | The plan in force now, status, period end, grace end, pending plan |
| `POST /api/v1/organizations/{id}/subscription` `{ "planKey": "PROFESSIONAL" }` | Free: immediate. Paid: returns `paymentReference`; the plan changes when the payment webhook arrives |
| `POST /api/v1/organizations/{id}/subscription/cancel` | Cancel at the end of the paid period |
| `GET /api/v1/organizations/{id}/billing-history` | Paginated payments (PAID / FAILED) |
| `POST /api/v1/webhooks/payment` | Payment provider events. No token; signed |

Errors: `PLAN_LIMIT_REACHED` (402) when a project or member would exceed the plan, `PLAN_NOT_FOUND` (404),
`ALREADY_ON_PLAN` (409), `WEBHOOK_SIGNATURE_INVALID` (401).

## Webhook

Body (JSON):

```json
{ "eventId": "evt_123", "type": "payment.succeeded", "paymentReference": "pay_...", "amountCents": 1500, "currency": "USD" }
```

`type` is `payment.succeeded`, `payment.failed` or `subscription.canceled`. `eventId` is the provider's
unique id for the delivery; sending the same id again answers `{"status":"DUPLICATE"}` and changes nothing.

Header: `X-Webhook-Signature: t=<unix seconds>,v1=<hex>` where `v1 = HMAC_SHA256(WEBHOOK_SECRET, t + "." + rawBody)`.
The timestamp must be within 5 minutes of the server clock.

### Simulating a payment by hand

1. `POST /api/v1/organizations/{id}/subscription` with `{"planKey":"PROFESSIONAL"}` and note `paymentReference`.
2. Compute the signature and send the webhook (bash, with the development secret):

```bash
BODY='{"eventId":"evt_demo_1","type":"payment.succeeded","paymentReference":"pay_REPLACE","amountCents":1500,"currency":"USD"}'
T=$(date +%s)
SIG=$(printf '%s.%s' "$T" "$BODY" | openssl dgst -sha256 -hmac 'local-development-only-webhook-secret' -hex | sed 's/^.* //')
curl -X POST http://localhost:8080/api/v1/webhooks/payment \
  -H "Content-Type: application/json" -H "X-Webhook-Signature: t=$T,v1=$SIG" -d "$BODY"
```

The response `status` values: `PLAN_ACTIVATED`, `RENEWED`, `PAST_DUE`, `CHECKOUT_FAILED`, `CANCELED`,
`CHECKOUT_CANCELED`, `DUPLICATE`, `REJECTED_AMOUNT_MISMATCH`, `IGNORED_UNKNOWN_REFERENCE`, `IGNORED_UNKNOWN_TYPE`.

See ADR-010 for the design.
