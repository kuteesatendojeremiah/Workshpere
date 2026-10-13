CREATE TABLE task_labels (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id     UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    label_id    UUID NOT NULL REFERENCES labels(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_task_labels_task_label UNIQUE (task_id, label_id)
);

CREATE INDEX idx_task_labels_task_id ON task_labels (task_id);
