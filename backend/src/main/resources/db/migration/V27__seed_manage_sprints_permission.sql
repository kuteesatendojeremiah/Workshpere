INSERT INTO permissions (key, description) VALUES
    ('MANAGE_SPRINTS', 'Create, start, complete sprints and plan which tasks belong to them');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ORGANIZATION_OWNER', 'PROJECT_MANAGER') AND p.key = 'MANAGE_SPRINTS';
