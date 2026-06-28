-- V33__add_payment_management.sql
-- Add checkout_url, expired_at columns; expand status CHECK; seed admin permissions.

-- 1. Add checkout_url and expired_at to orders
ALTER TABLE orders ADD COLUMN checkout_url text;
ALTER TABLE orders ADD COLUMN expired_at timestamptz;

-- 2. Expand status CHECK constraint to include 'expired'
ALTER TABLE orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('pending', 'paid', 'failed', 'refunded', 'cancelled', 'expired'));

-- 3. Seed admin payment permissions
INSERT INTO permissions (slug, module, resource, action, description) VALUES
  ('payment.admin:read',      'payment', 'payment.admin', 'read',      'Admin: xem danh sách đơn thanh toán'),
  ('payment.admin:analytics', 'payment', 'payment.admin', 'analytics', 'Admin: xem analytics thanh toán')
ON CONFLICT (slug) DO NOTHING;

-- 4. Grant both permissions to ADMIN role
UPDATE roles
SET permissions_json = permissions_json || '["payment.admin:read","payment.admin:analytics"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["payment.admin:read"]'::jsonb);

-- 5. Grant read-only permission to SUB_ADMIN role
UPDATE roles
SET permissions_json = permissions_json || '["payment.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["payment.admin:read"]'::jsonb);
