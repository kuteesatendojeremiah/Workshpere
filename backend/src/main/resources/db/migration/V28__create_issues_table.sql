CREATE TABLE issues (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    title           VARCHAR(255) NOT NULL,
    description     TEXT,
    severity        VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    priority        VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    environment     VARCHAR(50) NOT NULL DEFAULT 'PRODUCTION',
    status          VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    reporter_id     UUID NOT NULL REFERENCES users(id),
    assignee_id     UUID REFERENCES users(id),
    linked_task_id  UUID REFERENCES tasks(id),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_issues_project_id ON issues (project_id);
CREATE INDEX idx_issues_assignee_id ON issues (assignee_id);
