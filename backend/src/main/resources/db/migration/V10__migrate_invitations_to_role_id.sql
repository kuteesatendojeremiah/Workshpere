ALTER TABLE invitations ADD COLUMN role_id UUID;

UPDATE invitations SET role_id = (SELECT id FROM roles WHERE name = 'ORGANIZATION_OWNER') WHERE role = 'OWNER';
UPDATE invitations SET role_id = (SELECT id FROM roles WHERE name = 'EMPLOYEE_MEMBER') WHERE role = 'MEMBER';

ALTER TABLE invitations ALTER COLUMN role_id SET NOT NULL;
ALTER TABLE invitations ADD CONSTRAINT fk_invitations_role FOREIGN KEY (role_id) REFERENCES roles(id);
ALTER TABLE invitations DROP COLUMN role;
