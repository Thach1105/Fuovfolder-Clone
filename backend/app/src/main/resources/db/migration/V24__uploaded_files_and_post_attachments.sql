-- Upload metadata, forum attachments, catalog cover columns, media permissions.

alter table uploaded_files add column if not exists purpose varchar(64) not null default 'generic';
alter table uploaded_files add column if not exists original_mime varchar(120) null;
alter table uploaded_files add column if not exists linked_at timestamptz null;

create table if not exists post_attachments (
    id uuid primary key,
    post_id uuid not null,
    uploaded_file_id uuid not null,
    sort_order int not null default 0,
    created_at timestamptz not null default now()
);

create index if not exists ix_post_attachments_post on post_attachments(post_id, sort_order);
create index if not exists ix_uploaded_files_orphan on uploaded_files(created_at)
    where linked_at is null and deleted_at is null and status = 'active';

alter table source_catalog_items add column if not exists cover_image_url varchar(500) null;
alter table coursera_catalog_items add column if not exists cover_image_url varchar(500) null;
alter table membership_plans add column if not exists image_url varchar(500) null;

insert into permissions (slug, module, resource, action, description) values
('media.upload:create', 'media', 'upload', 'create', 'Upload file staging'),
('media.file:read', 'media', 'file', 'read', 'Download protected uploaded files')
on conflict (slug) do nothing;

update roles set permissions_json = permissions_json || '["media.upload:create","media.file:read"]'::jsonb
where slug in ('USER', 'FUO_MEMBER', 'FUO_VIP', 'FUO_NOVA');

update roles set permissions_json = permissions_json || '["media.upload:create","media.file:read"]'::jsonb
where slug in ('SUB_ADMIN', 'ADMIN', 'SUPER_ADMIN');
