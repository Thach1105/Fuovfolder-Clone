-- Forum crawl/sync pipeline support.
-- Adds: a system import user (author for crawled content), provenance columns on
-- threads/posts, an idempotent external->local id mapping table, and a sync run log.
-- No DB foreign keys per project decision; relationships validated in application code.

-- Fixed system user that owns all crawled threads/posts. Real crawled author handle
-- is preserved on the imported_author_handle columns below.
insert into users (
    id, username, username_normalized, email, normalized_email,
    display_name, status, email_verified_at, created_at, updated_at
)
values (
    '00000000-0000-0000-0000-0000000000fe',
    'fuoverflow_import',
    'fuoverflow_import',
    'import@fuoverflow.local',
    'import@fuoverflow.local',
    'FuOverflow Import',
    'ACTIVE',
    now(),
    now(),
    now()
)
on conflict do nothing;

alter table threads add column if not exists imported_author_handle varchar(190);
alter table threads add column if not exists source_url text;

alter table posts add column if not exists imported_author_handle varchar(190);
alter table posts add column if not exists source_url text;

-- Idempotent mapping between an external source record and the local row it created.
create table external_content_mappings (
    id uuid primary key,
    source varchar(64) not null,
    external_type varchar(32) not null,
    external_id varchar(190) not null,
    local_id uuid not null,
    content_checksum varchar(64) null,
    last_synced_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    constraint external_content_mappings_type_check
        check (external_type in ('forum', 'category', 'thread', 'post'))
);

create unique index ux_external_mapping_identity
    on external_content_mappings (source, external_type, external_id);
create index ix_external_mapping_local
    on external_content_mappings (external_type, local_id);

-- One row per crawl run, for observability and incremental scheduling.
create table forum_sync_runs (
    id uuid primary key,
    mode varchar(32) not null,
    scope varchar(32) not null,
    status varchar(32) not null default 'running',
    forums_synced int not null default 0,
    threads_synced int not null default 0,
    posts_synced int not null default 0,
    error_message text null,
    started_at timestamptz not null default now(),
    finished_at timestamptz null,
    created_at timestamptz not null default now(),
    constraint forum_sync_runs_mode_check check (mode in ('public', 'authenticated')),
    constraint forum_sync_runs_scope_check check (scope in ('full', 'incremental')),
    constraint forum_sync_runs_status_check check (status in ('running', 'succeeded', 'failed'))
);

create index ix_forum_sync_runs_status_started on forum_sync_runs (status, started_at desc);

create trigger trg_external_content_mappings_updated_at
    before update on external_content_mappings
    for each row execute function set_updated_at();
