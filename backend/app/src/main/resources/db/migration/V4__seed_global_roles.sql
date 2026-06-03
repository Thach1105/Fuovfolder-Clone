-- Seed global system roles (idempotent).
-- Application-level roles on users.roles_json use the same slug values: ADMIN, SUB_ADMIN, USER.

insert into roles (id, slug, name, scope, permissions_json)
select 'a0000000-0000-4000-8000-000000000001'::uuid, 'ADMIN', 'Administrator', 'global', '{}'::jsonb
where not exists (select 1 from roles where slug = 'ADMIN');

insert into roles (id, slug, name, scope, permissions_json)
select 'a0000000-0000-4000-8000-000000000002'::uuid, 'SUB_ADMIN', 'Sub Administrator', 'global', '{}'::jsonb
where not exists (select 1 from roles where slug = 'SUB_ADMIN');

insert into roles (id, slug, name, scope, permissions_json)
select 'a0000000-0000-4000-8000-000000000003'::uuid, 'USER', 'User', 'global', '{}'::jsonb
where not exists (select 1 from roles where slug = 'USER');
