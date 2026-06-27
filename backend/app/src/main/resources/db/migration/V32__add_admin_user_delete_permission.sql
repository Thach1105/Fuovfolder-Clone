-- Admin: soft-delete (vô hiệu hóa) tài khoản người dùng rác.

insert into permissions (slug, module, resource, action, description) values
('admin.user:delete', 'admin', 'user', 'delete', 'Xóa (vô hiệu hóa) tài khoản')
on conflict (slug) do nothing;

-- Cấp quyền cho ADMIN (SUPER_ADMIN tự động có mọi permission qua resolver).
update roles
set permissions_json = permissions_json || '["admin.user:delete"]'::jsonb
where slug = 'ADMIN'
  and not (permissions_json @> '["admin.user:delete"]'::jsonb);
