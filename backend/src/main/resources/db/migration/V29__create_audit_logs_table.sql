CREATE TABLE audit_logs (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id  UUID REFERENCES organizations(id) ON DELETE CASCADE,
    actor_id         UUID REFERENCES users(id),
    action           VARCHAR(100) NOT NULL,
    resource_type    VARCHAR(100) NOT NULL,
    resource_id      UUID,
    metadata         TEXT,
    request_id       VARCHAR(100),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_logs_organization_id ON audit_logs (organization_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs (created_at);
