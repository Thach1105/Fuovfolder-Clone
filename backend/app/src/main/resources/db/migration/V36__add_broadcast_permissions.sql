-- V36__add_broadcast_permissions.sql
-- Seed admin permissions for broadcast config management.

-- 1. Insert permission records
INSERT INTO permissions (slug, module, resource, action, description) VALUES
  ('broadcast.admin:read',   'broadcast', 'broadcast.admin', 'read',   'Admin: xem cấu hình thông báo toàn server'),
  ('broadcast.admin:update', 'broadcast', 'broadcast.admin', 'update', 'Admin: cập nhật cấu hình thông báo toàn server')
ON CONFLICT (slug) DO NOTHING;

-- 2. Grant both permissions to ADMIN role
UPDATE roles
SET permissions_json = permissions_json || '["broadcast.admin:read","broadcast.admin:update"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["broadcast.admin:read"]'::jsonb);

-- 3. Grant read-only to SUB_ADMIN role
UPDATE roles
SET permissions_json = permissions_json || '["broadcast.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["broadcast.admin:read"]'::jsonb);
