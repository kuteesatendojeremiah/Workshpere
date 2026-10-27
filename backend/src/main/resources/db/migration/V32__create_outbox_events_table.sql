-- Transactional outbox (spec 16.3): a business change and the event describing it are written in
-- the SAME database transaction. A relay publishes unpublished rows to Kafka afterwards, so
-- "database committed but the event was lost" cannot happen.
CREATE TABLE outbox_events (
    id              UUID PRIMARY KEY,
    event_type      VARCHAR(50)  NOT NULL,
    aggregate_type  VARCHAR(50)  NOT NULL,
    aggregate_id    UUID         NOT NULL,
    -- No foreign key on purpose: an event must outlive (and never block) changes to what it describes.
    organization_id UUID,
    -- The full JSON envelope that is sent to Kafka, stored as text so it is sent byte-for-byte.
    payload         TEXT         NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ,
    attempts        INT          NOT NULL DEFAULT 0,
    last_error      VARCHAR(500)
);

-- The relay only ever asks "what is not published yet, oldest first": a small partial index.
CREATE INDEX idx_outbox_events_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
-- Used by the daily clean-up of old published rows.
CREATE INDEX idx_outbox_events_published_at ON outbox_events (published_at) WHERE published_at IS NOT NULL;
