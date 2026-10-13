INSERT INTO permissions (key, description) VALUES
    ('MANAGE_PROJECTS', 'Create, update, archive projects and manage their membership'),
    ('VIEW_ALL_PROJECTS', 'View every project in the organization, not just ones directly joined');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('ORGANIZATION_OWNER', 'PROJECT_MANAGER') AND p.key IN ('MANAGE_PROJECTS', 'VIEW_ALL_PROJECTS');
