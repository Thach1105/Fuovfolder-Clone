-- Browser push subscription endpoints per user/device.

create table push_subscriptions (
    id uuid primary key,
    user_id uuid not null,
    endpoint text not null,
    p256dh varchar(255) not null,
    auth_key varchar(255) not null,
    user_agent text null,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index ux_push_subscriptions_endpoint on push_subscriptions(endpoint);
create index ix_push_subscriptions_user on push_subscriptions(user_id);
