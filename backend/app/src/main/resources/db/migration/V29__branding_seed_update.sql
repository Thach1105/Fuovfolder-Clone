-- Update seeded branding display text only.
-- Roles and forum-import source still keep their internal slugs
-- (FUO_MEMBER / FUO_VIP / FUO_NOVA, fuoverflow_import); only the
-- user-facing display strings are aligned with the new Fuexam brand.

update roles
   set name = 'Fuexam Member'
 where slug = 'FUO_MEMBER'
   and name = 'FUO Member';

update roles
   set name = 'Fuexam VIP'
 where slug = 'FUO_VIP'
   and name = 'FUO VIP';

update roles
   set name = 'Fuexam Nova'
 where slug = 'FUO_NOVA'
   and name = 'FUO Nova';

update permissions
   set description = 'Nhận Fuexam khi trả lời'
 where slug = 'points:earn_answer'
   and description = 'Nhận FUO khi trả lời';

update permissions
   set description = 'Xem số dư Fuexam'
 where slug = 'points:read'
   and description = 'Xem số dư FUO';

update permissions
   set description = 'Admin: điều chỉnh Fuexam'
 where slug = 'points.admin:update'
   and description = 'Admin: điều chỉnh FUO';

update users
   set display_name = 'Fuexam Import'
 where username = 'fuoverflow_import'
   and display_name = 'FuOverflow Import';
