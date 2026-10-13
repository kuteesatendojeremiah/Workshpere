ALTER TABLE organization_members ADD COLUMN role_id UUID;

UPDATE organization_members SET role_id = (SELECT id FROM roles WHERE name = 'ORGANIZATION_OWNER') WHERE role = 'OWNER';
UPDATE organization_members SET role_id = (SELECT id FROM roles WHERE name = 'EMPLOYEE_MEMBER') WHERE role = 'MEMBER';

ALTER TABLE organization_members ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE organization_members ADD CONSTRAINT fk_organization_members_role FOREIGN KEY (role_id) REFERENCES roles(id);
ALTER TABLE organization_members DROP COLUMN role;
