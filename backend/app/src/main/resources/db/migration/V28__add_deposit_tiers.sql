-- =================================================================
-- V28__add_deposit_tiers.sql
-- Deposit tier catalog + order snapshot columns + RBAC permissions.
-- =================================================================

create table deposit_tiers (
    id uuid primary key,
    label varchar(120) not null,
    amount_vnd int not null,
    points int not null,
    bonus_percent int not null default 0,
    is_active boolean not null default true,
    sort_order int not null default 0,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint deposit_tiers_amount_positive_check check (amount_vnd > 0),
    constraint deposit_tiers_points_nonneg_check check (points >= 0),
    constraint deposit_tiers_bonus_range_check check (bonus_percent between 0 and 100),
    constraint deposit_tiers_sort_nonneg_check check (sort_order >= 0)
);

create unique index ux_deposit_tiers_active_amount
    on deposit_tiers(amount_vnd) where is_active = true;

create index ix_deposit_tiers_active_sort
    on deposit_tiers(is_active, sort_order asc, amount_vnd asc);

alter table orders
    add column if not exists tier_id uuid null,
    add column if not exists points_awarded int null,
    add column if not exists tier_label_snapshot varchar(120) null;

create index ix_orders_tier on orders(tier_id) where tier_id is not null;

-- Seed permissions
insert into permissions (slug, module, resource, action, description) values
('deposit.admin:read', 'deposit', 'deposit.admin', 'read', 'Admin: xem danh sách deposit tier'),
('deposit.admin:update', 'deposit', 'deposit.admin', 'update', 'Admin: quản lý deposit tier')
on conflict (slug) do nothing;

-- Grant permissions to existing roles
update roles set permissions_json = (
    select to_jsonb(array_agg(distinct p))
    from (
        select jsonb_array_elements_text(permissions_json) as p
        from roles
        where slug = 'ADMIN'
        union
        select 'deposit.admin:read'
        union
        select 'deposit.admin:update'
    ) t
)
where slug = 'ADMIN';

update roles set permissions_json = (
    select to_jsonb(array_agg(distinct p))
    from (
        select jsonb_array_elements_text(permissions_json) as p
        from roles
        where slug = 'SUB_ADMIN'
        union
        select 'deposit.admin:read'
    ) t
)
where slug = 'SUB_ADMIN';
