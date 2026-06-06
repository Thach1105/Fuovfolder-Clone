-- Remove forum crawl/sync pipeline artifacts.

drop table if exists forum_sync_runs;
drop table if exists external_content_mappings;

delete from user_permission_overrides
where permission_slug in ('forum.sync:read', 'forum.sync:create');

delete from permissions
where slug in ('forum.sync:read', 'forum.sync:create');

update roles
set permissions_json = (
    select coalesce(jsonb_agg(to_jsonb(elem)), '[]'::jsonb)
    from jsonb_array_elements_text(permissions_json) as elem
    where elem not in ('forum.sync:read', 'forum.sync:create')
)
where permissions_json ?| array['forum.sync:read', 'forum.sync:create'];
