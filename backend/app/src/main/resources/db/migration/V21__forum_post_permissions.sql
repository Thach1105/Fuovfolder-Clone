-- Forum post edit/delete, reactions, flags, and moderation permissions.

insert into permissions (slug, module, resource, action, description) values
('forum.post:update', 'forum', 'post', 'update', 'Sửa bài viết của mình'),
('forum.post:delete', 'forum', 'post', 'delete', 'Xóa bài viết của mình'),
('forum.post:moderate', 'forum', 'post', 'moderate', 'Ẩn/xóa bài viết người khác'),
('forum.reaction:create', 'forum', 'reaction', 'create', 'Thêm reaction'),
('forum.reaction:delete', 'forum', 'reaction', 'delete', 'Bỏ reaction'),
('forum.flag:create', 'forum', 'flag', 'create', 'Báo cáo nội dung'),
('forum.moderation:read', 'forum', 'moderation', 'read', 'Xem queue moderation'),
('forum.moderation:update', 'forum', 'moderation', 'update', 'Xử lý flag/action')
on conflict (slug) do nothing;

update roles set permissions_json = permissions_json || '["forum.flag:create"]'::jsonb
where slug = 'USER';

update roles set permissions_json = permissions_json || '[
  "forum.post:update","forum.post:delete",
  "forum.reaction:create","forum.reaction:delete","forum.flag:create"
]'::jsonb
where slug = 'FUO_MEMBER';

update roles set permissions_json = permissions_json || '[
  "forum.post:moderate","forum.moderation:read","forum.moderation:update","forum.flag:create"
]'::jsonb
where slug in ('SUB_ADMIN', 'ADMIN');
