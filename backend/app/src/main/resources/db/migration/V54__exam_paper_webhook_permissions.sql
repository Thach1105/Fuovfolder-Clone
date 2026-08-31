-- Exam paper bank + webhook RBAC permissions (same shape as V44).
-- The webhook endpoint itself is authenticated by HMAC signature, not by a role, so no
-- permission is granted for calling it; these grants cover the admin review surface.

INSERT INTO permissions (slug, module, resource, action, description) VALUES
('exam.paper.admin:read', 'exam', 'paper.admin', 'read', 'Admin: xem đề FE/PE'),
('exam.paper.admin:update', 'exam', 'paper.admin', 'update', 'Admin: sửa đề FE/PE'),
('exam.paper.admin:delete', 'exam', 'paper.admin', 'delete', 'Admin: xóa đề FE/PE'),
('exam.paper.admin:publish', 'exam', 'paper.admin', 'publish', 'Admin: phát hành đề FE/PE'),
('exam.webhook.admin:read', 'exam', 'webhook.admin', 'read', 'Admin: xem log webhook nhận đề')
ON CONFLICT (slug) DO NOTHING;

-- SUB_ADMIN: read-only view of the paper bank and the ingest log.
UPDATE roles
SET permissions_json = permissions_json
    || '["exam.paper.admin:read","exam.webhook.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["exam.paper.admin:read"]'::jsonb);

-- ADMIN: full paper management including publishing.
UPDATE roles
SET permissions_json = permissions_json
    || '["exam.paper.admin:read","exam.paper.admin:update","exam.paper.admin:delete","exam.paper.admin:publish","exam.webhook.admin:read"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["exam.paper.admin:publish"]'::jsonb);
