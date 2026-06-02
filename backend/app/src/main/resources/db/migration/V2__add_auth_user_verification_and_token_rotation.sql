-- Add auth/user fields for email verification and rotating refresh tokens.
-- No foreign key constraints by design.

alter table users
    add column if not exists username_normalized varchar(64),
    add column if not exists normalized_email varchar(320),
    add column if not exists roles_json jsonb not null default '["USER"]'::jsonb,
    add column if not exists last_login_at timestamptz,
    add column if not exists password_changed_at timestamptz,
    add column if not exists first_name varchar(80),
    add column if not exists last_name varchar(80),
    add column if not exists email_verification_required boolean not null default true;

update users
set username_normalized = lower(username)
where username_normalized is null;

update users
set normalized_email = lower(email)
where normalized_email is null;

alter table users
    alter column username_normalized set not null,
    alter column normalized_email set not null;

create unique index if not exists ux_users_username_normalized_live
    on users (username_normalized)
    where deleted_at is null;

create unique index if not exists ux_users_normalized_email_live
    on users (normalized_email)
    where deleted_at is null;

create table if not exists email_verification_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(128) not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);

create unique index if not exists ux_email_verification_tokens_hash
    on email_verification_tokens (token_hash);
create index if not exists ix_email_verification_tokens_user
    on email_verification_tokens (user_id, created_at desc);
create index if not exists ix_email_verification_tokens_expires
    on email_verification_tokens (expires_at);

alter table user_sessions
    rename column token_hash to refresh_token_hash;

alter table user_sessions
    rename column expires_at to refresh_expires_at;

alter index if exists ux_user_sessions_token_hash rename to ux_user_sessions_refresh_hash;
alter index if exists ix_user_sessions_expires rename to ix_user_sessions_refresh_expires;

alter table user_sessions
    add column if not exists refresh_token_family_id uuid,
    add column if not exists refresh_token_jti uuid,
    add column if not exists access_token_jti uuid,
    add column if not exists issued_at timestamptz,
    add column if not exists access_expires_at timestamptz,
    add column if not exists last_used_at timestamptz,
    add column if not exists revoked_reason varchar(64),
    add column if not exists replaced_by_session_id uuid,
    add column if not exists metadata_json jsonb not null default '{}'::jsonb,
    add column if not exists version bigint not null default 0;

update user_sessions
set refresh_token_family_id = id
where refresh_token_family_id is null;

update user_sessions
set refresh_token_jti = id
where refresh_token_jti is null;

update user_sessions
set issued_at = created_at
where issued_at is null;

update user_sessions
set access_expires_at = created_at + interval '10 minutes'
where access_expires_at is null;

alter table user_sessions
    alter column refresh_token_family_id set not null,
    alter column refresh_token_jti set not null,
    alter column issued_at set not null,
    alter column access_expires_at set not null;

create unique index if not exists ux_user_sessions_refresh_jti
    on user_sessions (refresh_token_jti);
create index if not exists ix_user_sessions_family
    on user_sessions (refresh_token_family_id);
create index if not exists ix_user_sessions_user_active
    on user_sessions (user_id, revoked_at, refresh_expires_at);


alter table users drop constraint if exists users_status_check;

update users
set status = case status
    when 'active' then 'ACTIVE'
    when 'pending' then 'PENDING_EMAIL_VERIFICATION'
    when 'banned' then 'DISABLED'
    when 'deleted' then 'DELETED'
    else status
end;

alter table users add constraint users_status_check
    check (status in ('PENDING_EMAIL_VERIFICATION','ACTIVE','DISABLED','DELETED'));
