INSERT INTO permissions (key, description) VALUES
    ('MANAGE_TEAMS', 'Create, update, delete teams and manage their membership');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ORGANIZATION_OWNER', 'PROJECT_MANAGER') AND p.key = 'MANAGE_TEAMS';
