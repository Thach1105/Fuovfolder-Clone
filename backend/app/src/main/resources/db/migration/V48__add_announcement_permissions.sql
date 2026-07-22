INSERT INTO permissions (slug, module, resource, action, description) VALUES
  ('announcement.admin:read',  'broadcast', 'announcement.admin', 'read',  'Admin: xem danh sach thong bao toan server'),
  ('announcement.admin:write', 'broadcast', 'announcement.admin', 'write', 'Admin: tao/sua/xoa thong bao toan server')
ON CONFLICT (slug) DO NOTHING;

UPDATE roles
SET permissions_json = permissions_json || '["announcement.admin:read","announcement.admin:write"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["announcement.admin:read"]'::jsonb);

UPDATE roles
SET permissions_json = permissions_json || '["announcement.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["announcement.admin:read"]'::jsonb);
