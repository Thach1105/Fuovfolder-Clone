-- RBAC: permissions catalog, role extensions, user overrides, backfill assignments.

create table permissions (
    slug varchar(128) primary key,
    module varchar(64) not null,
    resource varchar(64) not null,
    action varchar(64) not null,
    description varchar(255) not null,
    is_system boolean not null default true,
    created_at timestamptz not null default now()
);

alter table roles
    add column if not exists role_type varchar(32) not null default 'SYSTEM',
    add column if not exists parent_role_id uuid null,
    add column if not exists is_system boolean not null default true,
    add column if not exists is_editable boolean not null default true;

alter table roles
    add constraint roles_role_type_check check (role_type in ('SYSTEM', 'STAFF', 'MEMBERSHIP'));

create table user_permission_overrides (
    id uuid primary key,
    user_id uuid not null,
    permission_slug varchar(128) not null,
    effect varchar(16) not null,
    reason text null,
    assigned_by_user_id uuid null,
    starts_at timestamptz not null default now(),
    ends_at timestamptz null,
    revoked_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint user_permission_overrides_effect_check check (effect in ('GRANT', 'DENY'))
);

create unique index ux_user_permission_override_active
    on user_permission_overrides(user_id, permission_slug)
    where revoked_at is null;

create index ix_user_permission_override_user on user_permission_overrides(user_id, revoked_at);

create table user_permission_versions (
    user_id uuid primary key,
    version bigint not null default 1,
    updated_at timestamptz not null default now()
);

-- Seed permissions catalog
insert into permissions (slug, module, resource, action, description) values
('forum:read', 'forum', 'forum', 'read', 'Xem diễn đàn'),
('forum.category:read', 'forum', 'category', 'read', 'Xem danh mục'),
('forum.thread:read', 'forum', 'thread', 'read', 'Xem chủ đề'),
('forum.thread:create', 'forum', 'thread', 'create', 'Tạo chủ đề'),
('forum.post:read', 'forum', 'post', 'read', 'Xem bài viết'),
('forum.post:create', 'forum', 'post', 'create', 'Bình luận / trả lời'),
('forum.post:bypass_moderation', 'forum', 'post', 'bypass_moderation', 'Đăng không cần duyệt'),
('forum.thread.bookmark:read', 'forum', 'thread.bookmark', 'read', 'Xem bookmark'),
('forum.thread.bookmark:create', 'forum', 'thread.bookmark', 'create', 'Theo dõi chủ đề'),
('forum.thread.bookmark:delete', 'forum', 'thread.bookmark', 'delete', 'Bỏ theo dõi chủ đề'),
('member.document:read', 'member', 'document', 'read', 'Xem tài liệu thành viên'),
('member.document:download', 'member', 'document', 'download', 'Tải tài liệu thành viên'),
('badge:read', 'badge', 'badge', 'read', 'Xem danh hiệu'),
('points:earn_answer', 'points', 'answer', 'earn', 'Nhận FUO khi trả lời'),
('ui.username_color', 'ui', 'username', 'color', 'Màu username đặc biệt'),
('ui.username_effect', 'ui', 'username', 'effect', 'Hiệu ứng username'),
('ui.avatar_effect', 'ui', 'avatar', 'effect', 'Hiệu ứng avatar'),
('user.profile:read', 'user', 'profile', 'read', 'Xem hồ sơ'),
('user.profile:update', 'user', 'profile', 'update', 'Cập nhật hồ sơ'),
('notification:read', 'notification', 'notification', 'read', 'Xem thông báo'),
('notification:update', 'notification', 'notification', 'update', 'Đánh dấu đã đọc'),
('notification.preference:read', 'notification', 'preference', 'read', 'Xem cài đặt thông báo'),
('notification.preference:update', 'notification', 'preference', 'update', 'Cập nhật cài đặt thông báo'),
('push.subscription:create', 'push', 'subscription', 'create', 'Đăng ký push'),
('push.subscription:delete', 'push', 'subscription', 'delete', 'Hủy push'),
('points:read', 'points', 'wallet', 'read', 'Xem số dư FUO'),
('source.catalog:read', 'source', 'catalog', 'read', 'Xem catalog Suộc'),
('source.question:read', 'source', 'question', 'read', 'Xem câu hỏi Suộc'),
('source.purchase:create', 'source', 'purchase', 'create', 'Mua tài liệu Suộc'),
('source.purchase:read', 'source', 'purchase', 'read', 'Xem đơn mua Suộc'),
('coursera.catalog:read', 'coursera', 'catalog', 'read', 'Xem catalog Coursera'),
('coursera.request:create', 'coursera', 'request', 'create', 'Tạo yêu cầu Coursera'),
('coursera.request:read', 'coursera', 'request', 'read', 'Xem yêu cầu Coursera'),
('admin.panel:access', 'admin', 'panel', 'access', 'Truy cập admin panel'),
('admin.overview:read', 'admin', 'overview', 'read', 'Xem tổng quan admin'),
('admin.user:read', 'admin', 'user', 'read', 'Xem danh sách user'),
('forum.sync:read', 'forum', 'sync', 'read', 'Xem lịch sử đồng bộ'),
('forum.sync:create', 'forum', 'sync', 'create', 'Kích hoạt đồng bộ'),
('source.catalog.admin:read', 'source', 'catalog.admin', 'read', 'Admin: xem catalog Suộc'),
('source.catalog.admin:create', 'source', 'catalog.admin', 'create', 'Admin: tạo catalog Suộc'),
('source.catalog.admin:update', 'source', 'catalog.admin', 'update', 'Admin: sửa catalog Suộc'),
('source.catalog.admin:delete', 'source', 'catalog.admin', 'delete', 'Admin: xóa catalog Suộc'),
('source.question.admin:read', 'source', 'question.admin', 'read', 'Admin: xem câu hỏi'),
('source.question.admin:create', 'source', 'question.admin', 'create', 'Admin: tạo câu hỏi'),
('source.question.admin:update', 'source', 'question.admin', 'update', 'Admin: sửa câu hỏi'),
('source.question.admin:delete', 'source', 'question.admin', 'delete', 'Admin: xóa câu hỏi'),
('source.media.admin:create', 'source', 'media.admin', 'create', 'Admin: upload media'),
('source.purchase.admin:read', 'source', 'purchase.admin', 'read', 'Admin: xem đơn mua'),
('source.purchase.admin:refund', 'source', 'purchase.admin', 'refund', 'Admin: hoàn tiền'),
('coursera.catalog.admin:read', 'coursera', 'catalog.admin', 'read', 'Admin: xem catalog Coursera'),
('coursera.catalog.admin:create', 'coursera', 'catalog.admin', 'create', 'Admin: tạo catalog Coursera'),
('coursera.catalog.admin:update', 'coursera', 'catalog.admin', 'update', 'Admin: sửa catalog Coursera'),
('coursera.catalog.admin:delete', 'coursera', 'catalog.admin', 'delete', 'Admin: xóa catalog Coursera'),
('coursera.request.admin:read', 'coursera', 'request.admin', 'read', 'Admin: xem yêu cầu'),
('coursera.request.admin:update', 'coursera', 'request.admin', 'update', 'Admin: cập nhật trạng thái'),
('points.admin:update', 'points', 'wallet.admin', 'update', 'Admin: điều chỉnh FUO'),
('auth.token:generate', 'auth', 'token', 'generate', 'Sinh token cho user'),
('rbac.role:read', 'rbac', 'role', 'read', 'Xem role và permission'),
('rbac.role:update', 'rbac', 'role', 'update', 'Sửa role và permission'),
('rbac.assignment:read', 'rbac', 'assignment', 'read', 'Xem gán role user'),
('rbac.assignment:update', 'rbac', 'assignment', 'update', 'Gán/thu hồi role user'),
('rbac.user_override:read', 'rbac', 'user_override', 'read', 'Xem override permission user'),
('rbac.user_override:update', 'rbac', 'user_override', 'update', 'Sửa override permission user'),
('membership.admin:read', 'membership', 'membership.admin', 'read', 'Admin: xem membership'),
('membership.admin:update', 'membership', 'membership.admin', 'update', 'Admin: quản lý membership'),
('membership.plan:read', 'membership', 'plan', 'read', 'Xem gói membership'),
('membership.subscribe:create', 'membership', 'subscribe', 'create', 'Đăng ký gói membership')
on conflict (slug) do nothing;

-- New roles
insert into roles (id, slug, name, scope, permissions_json, role_type, parent_role_id, is_system, is_editable)
select 'a0000000-0000-4000-8000-000000000004'::uuid, 'SUPER_ADMIN', 'Super Administrator', 'global', '[]'::jsonb,
       'SYSTEM', null, true, false
where not exists (select 1 from roles where slug = 'SUPER_ADMIN');

insert into roles (id, slug, name, scope, permissions_json, role_type, parent_role_id, is_system, is_editable)
select 'a0000000-0000-4000-8000-000000000005'::uuid, 'FUO_MEMBER', 'FUO Member', 'global', '[]'::jsonb,
       'MEMBERSHIP', 'a0000000-0000-4000-8000-000000000003'::uuid, true, true
where not exists (select 1 from roles where slug = 'FUO_MEMBER');

insert into roles (id, slug, name, scope, permissions_json, role_type, parent_role_id, is_system, is_editable)
select 'a0000000-0000-4000-8000-000000000006'::uuid, 'FUO_VIP', 'FUO VIP', 'global', '[]'::jsonb,
       'MEMBERSHIP', 'a0000000-0000-4000-8000-000000000005'::uuid, true, true
where not exists (select 1 from roles where slug = 'FUO_VIP');

insert into roles (id, slug, name, scope, permissions_json, role_type, parent_role_id, is_system, is_editable)
select 'a0000000-0000-4000-8000-000000000007'::uuid, 'FUO_NOVA', 'FUO Nova', 'global', '[]'::jsonb,
       'MEMBERSHIP', 'a0000000-0000-4000-8000-000000000006'::uuid, true, true
where not exists (select 1 from roles where slug = 'FUO_NOVA');

-- Update existing roles metadata
update roles set role_type = 'SYSTEM', parent_role_id = null, is_system = true, is_editable = true
where slug = 'USER';

update roles set role_type = 'STAFF', parent_role_id = 'a0000000-0000-4000-8000-000000000003'::uuid,
       is_system = true, is_editable = true
where slug in ('ADMIN', 'SUB_ADMIN');

-- USER base permissions
update roles set permissions_json = '[
  "forum:read","forum.category:read","forum.thread:read","forum.post:read",
  "forum.thread.bookmark:read","forum.thread.bookmark:create","forum.thread.bookmark:delete",
  "user.profile:read","user.profile:update",
  "notification:read","notification:update","notification.preference:read","notification.preference:update",
  "push.subscription:create","push.subscription:delete","points:read",
  "source.catalog:read","source.question:read","source.purchase:create","source.purchase:read",
  "coursera.catalog:read","coursera.request:create","coursera.request:read",
  "membership.plan:read","membership.subscribe:create"
]'::jsonb where slug = 'USER';

update roles set permissions_json = '[
  "forum.thread:create","forum.post:create",
  "member.document:read","member.document:download","badge:read"
]'::jsonb where slug = 'FUO_MEMBER';

update roles set permissions_json = '[
  "forum.post:bypass_moderation","points:earn_answer","ui.username_color"
]'::jsonb where slug = 'FUO_VIP';

update roles set permissions_json = '[
  "ui.username_effect","ui.avatar_effect"
]'::jsonb where slug = 'FUO_NOVA';

update roles set permissions_json = '[
  "admin.panel:access","admin.overview:read","admin.user:read",
  "forum.sync:read","forum.sync:create",
  "source.catalog.admin:read","source.question.admin:read","source.purchase.admin:read",
  "coursera.catalog.admin:read","coursera.request.admin:read","coursera.request.admin:update",
  "rbac.assignment:read","rbac.user_override:read",
  "membership.admin:read"
]'::jsonb where slug = 'SUB_ADMIN';

update roles set permissions_json = '[
  "admin.panel:access","admin.overview:read","admin.user:read",
  "forum.sync:read","forum.sync:create",
  "source.catalog.admin:read","source.catalog.admin:create","source.catalog.admin:update","source.catalog.admin:delete",
  "source.question.admin:read","source.question.admin:create","source.question.admin:update","source.question.admin:delete",
  "source.media.admin:create","source.purchase.admin:read","source.purchase.admin:refund",
  "coursera.catalog.admin:read","coursera.catalog.admin:create","coursera.catalog.admin:update","coursera.catalog.admin:delete",
  "coursera.request.admin:read","coursera.request.admin:update",
  "points.admin:update","auth.token:generate",
  "rbac.role:read","rbac.role:update","rbac.assignment:read","rbac.assignment:update",
  "rbac.user_override:read","rbac.user_override:update",
  "membership.admin:read","membership.admin:update"
]'::jsonb where slug = 'ADMIN';

update roles set permissions_json = '[]'::jsonb, is_editable = false
where slug = 'SUPER_ADMIN';

-- Backfill role_assignments from users.roles_json
insert into role_assignments (id, user_id, role_id, scope_type, scope_id, starts_at, created_at)
select gen_random_uuid(), u.id, r.id, 'global', null, u.created_at, now()
from users u
cross join lateral (
    select r2.id
    from roles r2
    where r2.slug = case
        when u.roles_json @> '["ADMIN"]'::jsonb then 'SUPER_ADMIN'
        when u.roles_json @> '["SUB_ADMIN"]'::jsonb then 'SUB_ADMIN'
        else 'USER'
    end
) r
where u.deleted_at is null
  and not exists (
      select 1 from role_assignments ra
      where ra.user_id = u.id and ra.role_id = r.id and ra.scope_type = 'global'
        and ra.revoked_at is null
  );

-- Permission versions for existing users
insert into user_permission_versions (user_id, version, updated_at)
select u.id, 1, now()
from users u
where u.deleted_at is null
on conflict (user_id) do nothing;

-- Seed membership plans
insert into membership_plans (id, slug, name, description, price_cents, currency, billing_interval, status, features_json)
select 'b0000000-0000-4000-8000-000000000001'::uuid, 'fuo-member', 'FUO MEMBER',
       'Gói thành viên 1 tháng', 48000, 'VND', 'month', 'active',
       '{"role_slug":"FUO_MEMBER","duration_days":30}'::jsonb
where not exists (select 1 from membership_plans where slug = 'fuo-member');

insert into membership_plans (id, slug, name, description, price_cents, currency, billing_interval, status, features_json)
select 'b0000000-0000-4000-8000-000000000002'::uuid, 'fuo-vip', 'FUO VIP',
       'Gói VIP 8 tháng', 200000, 'VND', 'month', 'active',
       '{"role_slug":"FUO_VIP","duration_days":240}'::jsonb
where not exists (select 1 from membership_plans where slug = 'fuo-vip');

insert into membership_plans (id, slug, name, description, price_cents, currency, billing_interval, status, features_json)
select 'b0000000-0000-4000-8000-000000000003'::uuid, 'fuo-nova', 'FUO NOVA',
       'Gói Nova 4 năm', 650000, 'VND', 'year', 'active',
       '{"role_slug":"FUO_NOVA","duration_days":1460}'::jsonb
where not exists (select 1 from membership_plans where slug = 'fuo-nova');
