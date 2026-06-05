-- Notification read paths at scale: unread badge, unread feed, bulk mark-read.

create index if not exists ix_notifications_user_unread_only
    on notifications(user_id, created_at desc)
    where read_at is null;

create index if not exists ix_notifications_user_created
    on notifications(user_id, created_at desc);
