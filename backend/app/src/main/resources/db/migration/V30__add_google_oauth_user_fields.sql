-- Add email_verified boolean to users
alter table users add column email_verified boolean not null default false;

-- Extend users.status check constraint to allow pending_profile
alter table users drop constraint if exists users_status_check;
alter table users add constraint users_status_check
  check (status in ('active','pending','banned','deleted','pending_profile'));

-- Enrich user_oauth_accounts with profile snapshot
alter table user_oauth_accounts
  add column display_name varchar(255) null,
  add column avatar_url   varchar(512) null,
  add column updated_at   timestamptz not null default now();
