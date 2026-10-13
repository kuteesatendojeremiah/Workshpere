INSERT INTO permissions (key, description) VALUES
    ('VIEW_AUDIT_LOGS', 'View the organization''s audit log history');

-- Deliberately ORGANIZATION_OWNER only (not PROJECT_MANAGER), per build-prompts.md Step 15:
-- "Add GET audit-logs for owners."
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ORGANIZATION_OWNER' AND p.key = 'VIEW_AUDIT_LOGS';
