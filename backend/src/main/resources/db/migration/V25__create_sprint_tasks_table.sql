CREATE TABLE sprint_tasks (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sprint_id     UUID NOT NULL REFERENCES sprints(id) ON DELETE CASCADE,
    task_id       UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    story_points  INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_sprint_tasks_sprint_task UNIQUE (sprint_id, task_id)
);

CREATE INDEX idx_sprint_tasks_sprint_id ON sprint_tasks (sprint_id);
CREATE INDEX idx_sprint_tasks_task_id ON sprint_tasks (task_id);
