-- Built by a Kafka consumer (reporting module) from domain events. The UNIQUE event_id is what makes
-- the consumer idempotent: Kafka delivers at-least-once, so the same event can arrive twice, and the
-- second insert is simply ignored.
CREATE TABLE activity_feed (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id        UUID         NOT NULL UNIQUE,
    event_type      VARCHAR(50)  NOT NULL,
    organization_id UUID,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    occurred_at     TIMESTAMPTZ  NOT NULL,
    recorded_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_activity_feed_org_time ON activity_feed (organization_id, occurred_at DESC);
