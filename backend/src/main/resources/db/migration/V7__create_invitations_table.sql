CREATE TABLE invitations (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id) ON DELETE CASCADE,
    email               VARCHAR(255) NOT NULL,
    role                VARCHAR(50) NOT NULL,
    invited_by_user_id  UUID NOT NULL REFERENCES users(id),
    token_hash          VARCHAR(255) NOT NULL,
    expires_at          TIMESTAMPTZ NOT NULL,
    accepted_at         TIMESTAMPTZ,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_invitations_token_hash UNIQUE (token_hash)
);

CREATE INDEX idx_invitations_organization_id ON invitations (organization_id);
