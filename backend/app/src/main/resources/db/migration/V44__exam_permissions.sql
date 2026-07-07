-- Exam (FE/PE) RBAC permissions.
-- NOTE on gating: unlocking full content requires an ACTIVE MEMBERSHIP of ANY tier.
-- A membership plan grants exactly one role (FUO_MEMBER / FUO_VIP / FUO_NOVA), so the
-- "is member" decision CANNOT be a static role permission. Instead the read/comment
-- permissions below are granted to the base USER role (authentication only), and the
-- ExamAccessGuard performs the real membership check via MembershipAccessPort at runtime.
-- Admin permissions are the usual per-action grants.

INSERT INTO permissions (slug, module, resource, action, description) VALUES
('exam.catalog:read', 'exam', 'catalog', 'read', 'Xem danh sách môn thi FE/PE'),
('exam.content:read', 'exam', 'content', 'read', 'Xem nội dung FE/PE (gate bằng membership)'),
('exam.comment:read', 'exam', 'comment', 'read', 'Xem bình luận FE/PE'),
('exam.comment:create', 'exam', 'comment', 'create', 'Tạo bình luận FE/PE'),
('exam.comment:update', 'exam', 'comment', 'update', 'Sửa bình luận FE/PE'),
('exam.comment:delete', 'exam', 'comment', 'delete', 'Xóa bình luận FE/PE'),
('exam.subject.admin:read', 'exam', 'subject.admin', 'read', 'Admin: xem môn thi'),
('exam.subject.admin:create', 'exam', 'subject.admin', 'create', 'Admin: tạo môn thi'),
('exam.subject.admin:update', 'exam', 'subject.admin', 'update', 'Admin: sửa môn thi'),
('exam.subject.admin:delete', 'exam', 'subject.admin', 'delete', 'Admin: xóa môn thi'),
('exam.question.admin:read', 'exam', 'question.admin', 'read', 'Admin: xem câu hỏi FE'),
('exam.question.admin:create', 'exam', 'question.admin', 'create', 'Admin: tạo câu hỏi FE'),
('exam.question.admin:update', 'exam', 'question.admin', 'update', 'Admin: sửa câu hỏi FE'),
('exam.question.admin:delete', 'exam', 'question.admin', 'delete', 'Admin: xóa câu hỏi FE'),
('exam.pe.admin:read', 'exam', 'pe.admin', 'read', 'Admin: xem đề PE'),
('exam.pe.admin:create', 'exam', 'pe.admin', 'create', 'Admin: tạo đề PE'),
('exam.pe.admin:update', 'exam', 'pe.admin', 'update', 'Admin: sửa đề PE'),
('exam.pe.admin:delete', 'exam', 'pe.admin', 'delete', 'Admin: xóa đề PE'),
('exam.media.admin:create', 'exam', 'media.admin', 'create', 'Admin: upload media FE/PE'),
('exam.comment.admin:delete', 'exam', 'comment.admin', 'delete', 'Admin: gỡ bình luận FE/PE')
ON CONFLICT (slug) DO NOTHING;

-- USER: browse + read content + comment (membership enforced at runtime by ExamAccessGuard).
UPDATE roles
SET permissions_json = permissions_json
    || '["exam.catalog:read","exam.content:read","exam.comment:read","exam.comment:create","exam.comment:update","exam.comment:delete"]'::jsonb
WHERE slug = 'USER'
  AND NOT (permissions_json @> '["exam.catalog:read"]'::jsonb);

-- SUB_ADMIN: read-only admin view.
UPDATE roles
SET permissions_json = permissions_json
    || '["exam.subject.admin:read","exam.question.admin:read","exam.pe.admin:read"]'::jsonb
WHERE slug = 'SUB_ADMIN'
  AND NOT (permissions_json @> '["exam.subject.admin:read"]'::jsonb);

-- ADMIN: full exam management.
UPDATE roles
SET permissions_json = permissions_json
    || '["exam.subject.admin:read","exam.subject.admin:create","exam.subject.admin:update","exam.subject.admin:delete","exam.question.admin:read","exam.question.admin:create","exam.question.admin:update","exam.question.admin:delete","exam.pe.admin:read","exam.pe.admin:create","exam.pe.admin:update","exam.pe.admin:delete","exam.media.admin:create","exam.comment.admin:delete"]'::jsonb
WHERE slug = 'ADMIN'
  AND NOT (permissions_json @> '["exam.subject.admin:create"]'::jsonb);
