-- Forum hierarchy: "Tài liệu" hub with two child document forums.
-- Repurpose tai-lieu-cac-mon-hoc as hub (slug tai-lieu), migrate leaf categories to children.

alter table forums add column if not exists parent_forum_id uuid null;
create index if not exists ix_forums_parent on forums(parent_forum_id) where deleted_at is null;

-- Hub forum (was tai-lieu-cac-mon-hoc)
update forums set
    slug = 'tai-lieu',
    title = 'Tài liệu',
    description = 'Kho tài liệu học tập FPT — đề thi và giáo trình',
    sort_order = 10,
    updated_at = now()
where id = 'f1000001-0000-0000-0000-000000000001'
  and deleted_at is null;

update forums set
    parent_forum_id = 'f1000001-0000-0000-0000-000000000001',
    sort_order = 11,
    updated_at = now()
where id = 'f1000002-0000-0000-0000-000000000002'
  and deleted_at is null;

update forums set
    parent_forum_id = 'f1000001-0000-0000-0000-000000000001',
    sort_order = 12,
    updated_at = now()
where id = 'f1000003-0000-0000-0000-000000000003'
  and deleted_at is null;

-- Missing subject categories in child forums (copied from legacy hub forum)
insert into categories (id, forum_id, parent_id, slug, title, sort_order, visibility, created_at, updated_at)
values
    ('c1000031-0000-0000-0000-000000000002', 'f1000003-0000-0000-0000-000000000003',
     'c1000003-0000-0000-0000-000000000001', 'prf192', 'PRF192', 1, 'public', now(), now()),
    ('c1000031-0000-0000-0000-000000000003', 'f1000003-0000-0000-0000-000000000003',
     'c1000003-0000-0000-0000-000000000001', 'caa201', 'CAA201', 2, 'public', now(), now())
on conflict do nothing;

-- Move threads from legacy hub categories to child forum categories
update threads set
    forum_id = 'f1000003-0000-0000-0000-000000000003',
    category_id = 'c1000031-0000-0000-0000-000000000001',
    updated_at = now()
where category_id in (
    'c1000011-0000-0000-0000-000000000001',
    'c1000011-0000-0000-0000-000000000003'
);

update threads set
    forum_id = 'f1000003-0000-0000-0000-000000000003',
    category_id = 'c1000031-0000-0000-0000-000000000002',
    updated_at = now()
where category_id = 'c1000011-0000-0000-0000-000000000002';

update threads set
    forum_id = 'f1000002-0000-0000-0000-000000000002',
    category_id = 'c1000021-0000-0000-0000-000000000001',
    updated_at = now()
where category_id = 'c1000011-0000-0000-0000-000000000004';

update threads set
    forum_id = 'f1000002-0000-0000-0000-000000000002',
    category_id = 'c1000021-0000-0000-0000-000000000002',
    updated_at = now()
where category_id = 'c1000011-0000-0000-0000-000000000005';

update threads set
    forum_id = 'f1000003-0000-0000-0000-000000000003',
    category_id = 'c1000031-0000-0000-0000-000000000003',
    updated_at = now()
where category_id = 'c1000011-0000-0000-0000-000000000006';

-- Any remaining threads still on the legacy hub forum
update threads set
    forum_id = 'f1000003-0000-0000-0000-000000000003',
    updated_at = now()
where forum_id = 'f1000001-0000-0000-0000-000000000001';

-- Soft-delete all categories belonging to the hub forum (hub has no category tree)
update categories set deleted_at = now(), updated_at = now()
where forum_id = 'f1000001-0000-0000-0000-000000000001'
  and deleted_at is null;
