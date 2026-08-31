INSERT INTO permissions (slug, module, resource, action, description) VALUES
  ('grading.paper.admin:read',   'grading', 'grading.paper.admin', 'read',   'Admin: xem bank de thi noi bo'),
  ('grading.paper.admin:create', 'grading', 'grading.paper.admin', 'create', 'Admin: nhap de thi tu payload JSON'),
  ('grading.paper.admin:update', 'grading', 'grading.paper.admin', 'update', 'Admin: tick dap an va phat hanh de'),
  ('grading.paper.admin:delete', 'grading', 'grading.paper.admin', 'delete', 'Admin: xoa de khoi bank')
ON CONFLICT (slug) DO NOTHING;

UPDATE roles
SET permissions_json = permissions_json || '["grading.paper.admin:read","grading.paper.admin:create","grading.paper.admin:update","grading.paper.admin:delete"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["grading.paper.admin:read"]'::jsonb);

UPDATE roles
SET permissions_json = permissions_json || '["grading.paper.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["grading.paper.admin:read"]'::jsonb);
