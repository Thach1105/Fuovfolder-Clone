-- Coursera service: catalog, combos (phase 2), service requests, credentials, audit events

create table coursera_catalog_items (
    id uuid primary key,
    code varchar(64) not null,
    title varchar(500) not null,
    description text null,
    price_points int not null,
    is_active boolean not null default true,
    is_featured boolean not null default false,
    sort_order int not null default 0,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint coursera_catalog_items_price_nonneg check (price_points >= 0)
);

create unique index ux_coursera_catalog_code_live
    on coursera_catalog_items (lower(code))
    where deleted_at is null;

create index ix_coursera_catalog_active_sort
    on coursera_catalog_items (is_active, sort_order)
    where deleted_at is null;

create table coursera_combos (
    id uuid primary key,
    code varchar(64) not null,
    title varchar(500) not null,
    description text null,
    price_points int not null,
    is_active boolean not null default true,
    sort_order int not null default 0,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    deleted_at timestamptz null,
    constraint coursera_combos_price_nonneg check (price_points >= 0)
);

create unique index ux_coursera_combo_code_live
    on coursera_combos (lower(code))
    where deleted_at is null;

create table coursera_combo_items (
    id uuid primary key,
    combo_id uuid not null,
    catalog_item_id uuid not null,
    quantity int not null default 1,
    created_at timestamptz not null default now(),
    constraint coursera_combo_items_qty_positive check (quantity > 0)
);

create unique index ux_coursera_combo_items_pair
    on coursera_combo_items (combo_id, catalog_item_id);

create index ix_coursera_combo_items_combo on coursera_combo_items (combo_id);

create table coursera_service_requests (
    id uuid primary key,
    user_id uuid not null,
    status varchar(32) not null default 'pending',
    total_points int not null,
    pricing_kind varchar(16) not null default 'single',
    combo_id uuid null,
    user_notes text null,
    assigned_to_user_id uuid null,
    status_changed_at timestamptz not null default now(),
    status_changed_by uuid null,
    payment_ledger_id uuid null,
    refund_ledger_id uuid null,
    idempotency_key varchar(255) null,
    lock_version int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint coursera_service_requests_status_check
        check (status in ('pending', 'in_progress', 'completed', 'cancelled')),
    constraint coursera_service_requests_pricing_kind_check
        check (pricing_kind in ('single', 'combo')),
    constraint coursera_service_requests_total_nonneg check (total_points >= 0)
);

create unique index ux_coursera_requests_idempotency
    on coursera_service_requests (user_id, idempotency_key)
    where idempotency_key is not null;

create index ix_coursera_requests_user_created
    on coursera_service_requests (user_id, created_at desc);

create index ix_coursera_requests_status_created
    on coursera_service_requests (status, created_at desc);

create table coursera_request_items (
    id uuid primary key,
    request_id uuid not null,
    catalog_item_id uuid not null,
    item_title_snapshot varchar(500) not null,
    unit_price_points int not null,
    quantity int not null default 1,
    created_at timestamptz not null default now(),
    constraint coursera_request_items_qty_positive check (quantity > 0),
    constraint coursera_request_items_unit_price_nonneg check (unit_price_points >= 0)
);

create index ix_coursera_request_items_request on coursera_request_items (request_id);

create table coursera_request_credentials (
    id uuid primary key,
    request_id uuid not null,
    account_label varchar(120) null,
    coursera_email varchar(255) not null,
    password_ciphertext text not null,
    password_key_id varchar(64) not null default 'default',
    created_at timestamptz not null default now()
);

create unique index ux_coursera_request_credentials_request
    on coursera_request_credentials (request_id);

create table coursera_request_status_events (
    id uuid primary key,
    request_id uuid not null,
    from_status varchar(32) null,
    to_status varchar(32) not null,
    actor_user_id uuid null,
    note text null,
    created_at timestamptz not null default now(),
    constraint coursera_request_status_events_to_status_check
        check (to_status in ('pending', 'in_progress', 'completed', 'cancelled'))
);

create index ix_coursera_request_events_request
    on coursera_request_status_events (request_id, created_at desc);
