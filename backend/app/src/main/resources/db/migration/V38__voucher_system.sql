-- Voucher definitions
create table vouchers (
    id                       uuid primary key,
    code                     varchar(50)  not null,
    description              text,
    discount_type            varchar(16)  not null,
    discount_value           int          not null,
    max_discount_points      int,
    min_order_points         int          not null default 0,
    max_usage                int          not null,
    used_count               int          not null default 0,
    max_usage_per_user       int          not null default 1,
    applicable_types         varchar(255) not null,
    required_membership_slugs varchar(255),
    starts_at                timestamptz  not null,
    ends_at                  timestamptz  not null,
    active                   boolean      not null default true,
    created_by               uuid         not null,
    created_at               timestamptz  not null default now(),
    updated_at               timestamptz  not null default now(),
    constraint chk_voucher_discount_type check (discount_type in ('percentage', 'fixed')),
    constraint chk_voucher_discount_value check (discount_value > 0),
    constraint chk_voucher_max_usage check (max_usage > 0),
    constraint chk_voucher_dates check (ends_at > starts_at)
);

create unique index uq_vouchers_code_active on vouchers (upper(code)) where active = true;
create index idx_vouchers_active_dates on vouchers (active, starts_at, ends_at);

-- User-specific voucher assignments
create table voucher_user_assignments (
    id          uuid primary key,
    voucher_id  uuid        not null,
    user_id     uuid        not null,
    created_at  timestamptz not null default now()
);

create unique index uq_voucher_user_assignment on voucher_user_assignments (voucher_id, user_id);

-- Redemption history
create table voucher_redemptions (
    id               uuid primary key,
    voucher_id       uuid        not null,
    user_id          uuid        not null,
    transaction_type varchar(32) not null,
    transaction_id   uuid        not null,
    original_points  int         not null,
    discount_points  int         not null,
    final_points     int         not null,
    created_at       timestamptz not null default now()
);

create index idx_voucher_redemptions_voucher_user on voucher_redemptions (voucher_id, user_id);
create index idx_voucher_redemptions_transaction on voucher_redemptions (transaction_type, transaction_id);

-- RBAC permissions
insert into permissions (slug, module, resource, action, description) values
    ('voucher.admin:read',   'voucher', 'voucher.admin', 'read',   'Admin: xem danh sách voucher'),
    ('voucher.admin:create', 'voucher', 'voucher.admin', 'create', 'Admin: tạo voucher mới'),
    ('voucher.admin:update', 'voucher', 'voucher.admin', 'update', 'Admin: cập nhật voucher'),
    ('voucher.admin:delete', 'voucher', 'voucher.admin', 'delete', 'Admin: xóa voucher')
on conflict (slug) do nothing;

update roles
set permissions_json = permissions_json || '["voucher.admin:read","voucher.admin:create","voucher.admin:update","voucher.admin:delete"]'::jsonb
where slug = 'ADMIN'
  and not (permissions_json @> '["voucher.admin:read"]'::jsonb);

update roles
set permissions_json = permissions_json || '["voucher.admin:read"]'::jsonb
where slug = 'SUB_ADMIN'
  and not (permissions_json @> '["voucher.admin:read"]'::jsonb);
