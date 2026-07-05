-- Admin: cập nhật cấu hình tài khoản (device limit, v.v.)

INSERT INTO permissions (slug, module, resource, action, description) VALUES
('admin.user:update', 'admin', 'user', 'update', 'Cập nhật cấu hình tài khoản người dùng')
ON CONFLICT (slug) DO NOTHING;

-- Cấp quyền cho ADMIN (SUPER_ADMIN tự động có mọi permission qua resolver).
UPDATE roles
SET permissions_json = permissions_json || '["admin.user:update"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["admin.user:update"]'::jsonb);
