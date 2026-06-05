-- Query indexes for large-scale forum reads and bookmark joins.

-- Live-thread browse: forum/category filters + sort by last activity
create index if not exists ix_threads_browse_live
    on threads(forum_id, category_id, last_post_at desc, created_at desc)
    where deleted_at is null and status not in ('hidden', 'deleted');

-- Category stats aggregation scans category_id on live threads only
create index if not exists ix_threads_category_stats_live
    on threads(category_id)
    include (reply_count, last_post_at, title, thread_type, imported_author_handle)
    where deleted_at is null;

-- Bookmark list: user -> threads ordered by bookmark time
create index if not exists ix_thread_bookmarks_user_created
    on thread_bookmarks(user_id, created_at desc);

-- Reverse lookup: is thread watched by user (toggle / status check)
create index if not exists ix_thread_bookmarks_thread_user
    on thread_bookmarks(thread_id, user_id);

-- Post list per thread excludes soft-deleted rows
create index if not exists ix_posts_thread_created_live
    on posts(thread_id, created_at)
    where deleted_at is null;
