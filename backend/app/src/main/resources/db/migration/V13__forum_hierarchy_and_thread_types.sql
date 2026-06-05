-- Hierarchical categories, multi-type threads, poll options, and document forum seed data.

alter table categories add column if not exists parent_id uuid null;
alter table categories add column if not exists icon_color varchar(32) null;
create index if not exists ix_categories_parent_sort on categories(parent_id, sort_order) where deleted_at is null;

alter table threads add column if not exists thread_type varchar(32) not null default 'discussion';
alter table threads add constraint threads_thread_type_check
    check (thread_type in ('discussion', 'article', 'poll', 'question'));

alter table threads add column if not exists campus varchar(64) null;
alter table threads add column if not exists semester varchar(32) null;
alter table threads add column if not exists material_type varchar(64) null;
alter table threads add column if not exists tags varchar(500) null;

create table if not exists poll_options (
    id uuid primary key,
    thread_id uuid not null,
    label varchar(255) not null,
    vote_count int not null default 0,
    sort_order int not null default 0,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists ix_poll_options_thread on poll_options(thread_id, sort_order);

-- Document forums (Tài liệu các môn học, Đề thi, Giáo trình)
insert into forums (id, slug, title, description, visibility, sort_order, created_at, updated_at)
values
    ('f1000001-0000-0000-0000-000000000001', 'tai-lieu-cac-mon-hoc',
     'Tài liệu các môn học', 'Danh sách tất cả các môn học tại FPT', 'public', 10, now(), now()),
    ('f1000002-0000-0000-0000-000000000002', 'de-thi-cac-ky-truoc',
     'Đề thi các kỳ trước', 'Kho đề thi FE, PE các kỳ trước', 'public', 20, now(), now()),
    ('f1000003-0000-0000-0000-000000000003', 'giao-trinh-slide-code-mau',
     'Giáo trình - Slide - Code mẫu', 'Giáo trình, slide bài giảng và code mẫu', 'public', 30, now(), now())
on conflict do nothing;

-- Semester parent categories + subject children for tai-lieu-cac-mon-hoc
insert into categories (id, forum_id, parent_id, slug, title, description, sort_order, visibility, icon_color, created_at, updated_at)
values
    ('c1000001-0000-0000-0000-000000000001', 'f1000001-0000-0000-0000-000000000001', null,
     'tong-hop', 'Tổng hợp - Chưa rõ kỳ', null, 0, 'public', '#64748b', now(), now()),
    ('c1000001-0000-0000-0000-000000000002', 'f1000001-0000-0000-0000-000000000001', null,
     'ky-1', 'Kỳ 1', null, 1, 'public', '#2563eb', now(), now()),
    ('c1000001-0000-0000-0000-000000000003', 'f1000001-0000-0000-0000-000000000001', null,
     'ky-2', 'Kỳ 2', null, 2, 'public', '#dc2626', now(), now()),
    ('c1000001-0000-0000-0000-000000000004', 'f1000001-0000-0000-0000-000000000001', null,
     'ky-3', 'Kỳ 3', null, 3, 'public', '#16a34a', now(), now())
on conflict do nothing;

insert into categories (id, forum_id, parent_id, slug, title, sort_order, visibility, created_at, updated_at)
values
    ('c1000011-0000-0000-0000-000000000001', 'f1000001-0000-0000-0000-000000000001',
     'c1000001-0000-0000-0000-000000000001', 'mad101', 'MAD101', 0, 'public', now(), now()),
    ('c1000011-0000-0000-0000-000000000002', 'f1000001-0000-0000-0000-000000000001',
     'c1000001-0000-0000-0000-000000000002', 'prf192', 'PRF192', 0, 'public', now(), now()),
    ('c1000011-0000-0000-0000-000000000003', 'f1000001-0000-0000-0000-000000000001',
     'c1000001-0000-0000-0000-000000000002', 'mad101-ky1', 'MAD101', 1, 'public', now(), now()),
    ('c1000011-0000-0000-0000-000000000004', 'f1000001-0000-0000-0000-000000000001',
     'c1000001-0000-0000-0000-000000000002', 'pro192', 'PRO192', 2, 'public', now(), now()),
    ('c1000011-0000-0000-0000-000000000005', 'f1000001-0000-0000-0000-000000000001',
     'c1000001-0000-0000-0000-000000000003', 'prn212', 'PRN212', 0, 'public', now(), now()),
    ('c1000011-0000-0000-0000-000000000006', 'f1000001-0000-0000-0000-000000000001',
     'c1000001-0000-0000-0000-000000000003', 'caa201', 'CAA201', 1, 'public', now(), now())
on conflict do nothing;

-- Same semester structure for đề thi forum
insert into categories (id, forum_id, parent_id, slug, title, sort_order, visibility, icon_color, created_at, updated_at)
values
    ('c1000002-0000-0000-0000-000000000001', 'f1000002-0000-0000-0000-000000000002', null,
     'tong-hop', 'Tổng hợp - Chưa rõ kỳ', 0, 'public', '#64748b', now(), now()),
    ('c1000002-0000-0000-0000-000000000002', 'f1000002-0000-0000-0000-000000000002', null,
     'ky-1', 'Kỳ 1', 1, 'public', '#2563eb', now(), now()),
    ('c1000002-0000-0000-0000-000000000003', 'f1000002-0000-0000-0000-000000000002', null,
     'ky-2', 'Kỳ 2', 2, 'public', '#dc2626', now(), now())
on conflict do nothing;

insert into categories (id, forum_id, parent_id, slug, title, sort_order, visibility, created_at, updated_at)
values
    ('c1000021-0000-0000-0000-000000000001', 'f1000002-0000-0000-0000-000000000002',
     'c1000002-0000-0000-0000-000000000002', 'pro192', 'PRO192', 0, 'public', now(), now()),
    ('c1000021-0000-0000-0000-000000000002', 'f1000002-0000-0000-0000-000000000002',
     'c1000002-0000-0000-0000-000000000003', 'prn212', 'PRN212', 0, 'public', now(), now())
on conflict do nothing;

-- Giáo trình forum
insert into categories (id, forum_id, parent_id, slug, title, sort_order, visibility, icon_color, created_at, updated_at)
values
    ('c1000003-0000-0000-0000-000000000001', 'f1000003-0000-0000-0000-000000000003', null,
     'tong-hop', 'Tổng hợp - Chưa rõ kỳ', 0, 'public', '#64748b', now(), now()),
    ('c1000003-0000-0000-0000-000000000002', 'f1000003-0000-0000-0000-000000000003', null,
     'ky-1', 'Kỳ 1', 1, 'public', '#2563eb', now(), now())
on conflict do nothing;

insert into categories (id, forum_id, parent_id, slug, title, sort_order, visibility, created_at, updated_at)
values
    ('c1000031-0000-0000-0000-000000000001', 'f1000003-0000-0000-0000-000000000003',
     'c1000003-0000-0000-0000-000000000002', 'mad101', 'MAD101', 0, 'public', now(), now())
on conflict do nothing;
