-- Source (Suộc) service: exam material catalog, related items, purchases, purchase audit events.
-- FUO Point payments reuse points_ledger (award module). No DB foreign keys per project decision.

create table source_catalog_items (
    id uuid primary key,
    code varchar(64) not null,
    title varchar(500) not null,
    description text null,
    price_points int not null,
    access_days int not null default 60,
    question_count int not null default 0,
    duplication_rate_bp int not null default 0, -- percentage x100 (e.g. 154.72% -> 15472)
    pass_rate_bp int not null default 0,        -- percentage x100 (e.g. 75% -> 7500)
    view_count bigint not null default 0,
    card_color varchar(32) null,
    category_slug varchar(64) null,
    is_active boolean not null default true,
    is_featured boolean not null default false,
    sort_order int not null default 0,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint source_catalog_items_price_nonneg check (price_points >= 0),
    constraint source_catalog_items_access_days_positive check (access_days > 0),
    constraint source_catalog_items_question_count_nonneg check (question_count >= 0)
);

create unique index ux_source_catalog_code_live
    on source_catalog_items (lower(code))
    where deleted_at is null;

create index ix_source_catalog_active_featured_sort
    on source_catalog_items (is_active, is_featured, sort_order)
    where deleted_at is null;

create table source_related_items (
    id uuid primary key,
    catalog_item_id uuid not null,
    related_catalog_item_id uuid not null,
    sort_order int not null default 0,
    created_at timestamptz not null default now()
);

create unique index ux_source_related_pair
    on source_related_items (catalog_item_id, related_catalog_item_id);

create index ix_source_related_catalog
    on source_related_items (catalog_item_id, sort_order);

create table source_purchases (
    id uuid primary key,
    user_id uuid not null,
    catalog_item_id uuid not null,
    code_snapshot varchar(64) not null,
    title_snapshot varchar(500) not null,
    status varchar(32) not null default 'active',
    unit_price_points int not null,
    access_days_snapshot int not null,
    starts_at timestamptz not null default now(),
    ends_at timestamptz not null,
    payment_ledger_id uuid null,
    refund_ledger_id uuid null,
    refund_reason varchar(500) null,
    idempotency_key varchar(255) null,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint source_purchases_status_check
        check (status in ('active', 'expired', 'refunded', 'cancelled')),
    constraint source_purchases_price_nonneg check (unit_price_points >= 0),
    constraint source_purchases_access_days_positive check (access_days_snapshot > 0)
);

create unique index ux_source_purchases_idempotency
    on source_purchases (user_id, idempotency_key)
    where idempotency_key is not null;

create index ix_source_purchases_user_status_ends
    on source_purchases (user_id, status, ends_at desc);

create index ix_source_purchases_user_created
    on source_purchases (user_id, created_at desc);

create index ix_source_purchases_catalog_user
    on source_purchases (catalog_item_id, user_id, status);

create table source_purchase_events (
    id uuid primary key,
    purchase_id uuid not null,
    event_type varchar(32) not null,
    from_status varchar(32) null,
    to_status varchar(32) null,
    actor_user_id uuid null,
    note varchar(500) null,
    created_at timestamptz not null default now(),
    constraint source_purchase_events_type_check
        check (event_type in ('purchase', 'extend', 'refund', 'expire'))
);

create index ix_source_purchase_events_purchase
    on source_purchase_events (purchase_id, created_at desc);

create trigger trg_source_catalog_items_updated_at
    before update on source_catalog_items
    for each row execute function set_updated_at();

create trigger trg_source_purchases_updated_at
    before update on source_purchases
    for each row execute function set_updated_at();
