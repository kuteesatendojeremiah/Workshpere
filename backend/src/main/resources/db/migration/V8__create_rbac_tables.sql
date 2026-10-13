CREATE TABLE permissions (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    key         VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_permissions_key UNIQUE (key)
);

CREATE TABLE roles (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT uq_roles_name UNIQUE (name)
);

CREATE TABLE role_permissions (
    role_id       UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    permission_id UUID NOT NULL REFERENCES permissions(id) ON DELETE CASCADE,

    PRIMARY KEY (role_id, permission_id)
);

INSERT INTO permissions (key, description) VALUES
    ('VIEW_ORGANIZATION', 'View organization details and member list'),
    ('MANAGE_ORGANIZATION_MEMBERS', 'Invite members and manage organization membership');

-- Roles from spec section 3. PLATFORM_ADMIN is seeded for completeness but not yet assignable
-- or enforced anywhere - the Administration module that would use it doesn't exist yet.
INSERT INTO roles (name, description) VALUES
    ('PLATFORM_ADMIN', 'Organizations, platform configuration, plans, support, and global audit'),
    ('ORGANIZATION_OWNER', 'Organization settings, members, teams, projects, billing'),
    ('PROJECT_MANAGER', 'Projects, sprints, tasks, assignments, reports'),
    ('DEVELOPER', 'Assigned work, status updates, comments, files, collaboration'),
    ('EMPLOYEE_MEMBER', 'Participate in projects and manage assigned work');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'ORGANIZATION_OWNER' AND p.key IN ('VIEW_ORGANIZATION', 'MANAGE_ORGANIZATION_MEMBERS');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('PROJECT_MANAGER', 'DEVELOPER', 'EMPLOYEE_MEMBER') AND p.key = 'VIEW_ORGANIZATION';
