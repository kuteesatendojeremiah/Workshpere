ALTER TABLE tasks ADD COLUMN position INT NOT NULL DEFAULT 0;

CREATE INDEX idx_tasks_project_status_position ON tasks (project_id, status, position);
