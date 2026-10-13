CREATE TABLE comment_mentions (
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    comment_id         UUID NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    mentioned_user_id  UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_comment_mentions_comment_user UNIQUE (comment_id, mentioned_user_id)
);

CREATE INDEX idx_comment_mentions_mentioned_user_id ON comment_mentions (mentioned_user_id);
