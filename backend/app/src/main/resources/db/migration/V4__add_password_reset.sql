-- Add password reset tokens table for forgot password functionality.
-- No foreign key constraints by design.

create table if not exists password_reset_tokens (
    id uuid primary key,
    user_id uuid not null,
    token_hash varchar(128) not null,
    expires_at timestamptz not null,
    consumed_at timestamptz,
    created_at timestamptz not null default now()
);

create unique index if not exists ux_password_reset_tokens_hash
    on password_reset_tokens (token_hash);

create index if not exists ix_password_reset_tokens_user
    on password_reset_tokens (user_id, created_at desc);

create index if not exists ix_password_reset_tokens_expires
    on password_reset_tokens (expires_at);
