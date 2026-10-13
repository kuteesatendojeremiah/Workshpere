CREATE TABLE task_dependencies (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id             UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    depends_on_task_id  UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_task_dependencies_pair UNIQUE (task_id, depends_on_task_id),
    CONSTRAINT chk_task_dependencies_not_self CHECK (task_id <> depends_on_task_id)
);

CREATE INDEX idx_task_dependencies_task_id ON task_dependencies (task_id);
