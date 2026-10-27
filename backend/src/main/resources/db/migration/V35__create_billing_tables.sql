-- Plans, subscriptions, payment webhooks and billing history (spec section 20).

CREATE TABLE plans (
    plan_key             VARCHAR(30)  PRIMARY KEY,
    name                 VARCHAR(100) NOT NULL,
    monthly_price_cents  INT          NOT NULL CHECK (monthly_price_cents >= 0),
    -- NULL means "unlimited".
    max_projects         INT          CHECK (max_projects IS NULL OR max_projects > 0),
    max_members          INT          CHECK (max_members IS NULL OR max_members > 0)
);

INSERT INTO plans (plan_key, name, monthly_price_cents, max_projects, max_members) VALUES
    ('FREE',         'Free',         0,    3,    5),
    ('PROFESSIONAL', 'Professional', 1500, 50,   50),
    ('ENTERPRISE',   'Enterprise',   9900, NULL, NULL);

-- One row per organization that has ever left the implicit Free plan.
CREATE TABLE subscriptions (
    id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id          UUID        NOT NULL UNIQUE REFERENCES organizations(id) ON DELETE CASCADE,
    plan_key                 VARCHAR(30) NOT NULL REFERENCES plans(plan_key),
    status                   VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'PAST_DUE', 'CANCELED')),
    current_period_end       TIMESTAMPTZ,
    -- After a failed payment the paid plan keeps working until this moment (grace period).
    grace_ends_at            TIMESTAMPTZ,
    cancel_at_period_end     BOOLEAN     NOT NULL DEFAULT FALSE,
    -- A plan chosen but not yet paid for; it only becomes the plan when the payment webhook arrives.
    pending_plan_key         VARCHAR(30) REFERENCES plans(plan_key),
    pending_payment_ref      VARCHAR(100) UNIQUE,
    -- The payment reference of the active paid plan; renewals and failures arrive with this reference.
    payment_ref              VARCHAR(100) UNIQUE,
    version                  BIGINT      NOT NULL DEFAULT 0,
    created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Every webhook delivery we have accepted, keyed by the PROVIDER's event id. The UNIQUE constraint is
-- what makes processing idempotent: a duplicate delivery cannot insert a second row.
CREATE TABLE payment_events (
    provider_event_id VARCHAR(100) PRIMARY KEY,
    event_type        VARCHAR(50)  NOT NULL,
    organization_id   UUID,
    outcome           VARCHAR(30)  NOT NULL,
    payload           TEXT         NOT NULL,
    received_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE billing_history (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id   UUID         NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    plan_key          VARCHAR(30)  NOT NULL REFERENCES plans(plan_key),
    amount_cents      INT          NOT NULL CHECK (amount_cents >= 0),
    currency          VARCHAR(3)  NOT NULL,
    status            VARCHAR(20)  NOT NULL CHECK (status IN ('PAID', 'FAILED')),
    description       VARCHAR(200) NOT NULL,
    provider_event_id VARCHAR(100) NOT NULL UNIQUE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_billing_history_org_time ON billing_history (organization_id, created_at DESC);

INSERT INTO permissions (key, description) VALUES
    ('MANAGE_BILLING', 'Change the subscription plan, cancel it, and see billing history');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ORGANIZATION_OWNER' AND p.key = 'MANAGE_BILLING';
