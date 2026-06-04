-- Seed sample Suộc exam materials for local/dev. Idempotent on id.

insert into source_catalog_items
    (id, code, title, description, price_points, access_days, question_count,
     duplication_rate_bp, pass_rate_bp, card_color, category_slug,
     is_active, is_featured, sort_order, lock_version, created_at, updated_at)
values
    ('b1000000-0000-4000-8000-000000000001', 'MLN111', 'MLN111 - Triết học Mác - Lênin',
     'Ngân hàng câu hỏi ôn thi MLN111 với tỷ lệ trùng lặp cao.', 90000, 60, 583,
     15472, 0, '#8e3b5e', 'on-thi', true, true, 10, 0, now(), now()),
    ('b1000000-0000-4000-8000-000000000002', 'CSI106', 'CSI106 - Introduction to Computer Science',
     'Tài liệu ôn thi CSI106.', 37200, 60, 455, 6035, 0, '#7c3aed', 'on-thi',
     true, true, 20, 0, now(), now()),
    ('b1000000-0000-4000-8000-000000000003', 'PRN212', 'PRN212 - Basic Cross-Platform Application Programming',
     'Tài liệu ôn thi PRN212.', 90000, 60, 270, 11926, 0, '#6d28d9', 'on-thi',
     true, true, 30, 0, now(), now()),
    ('b1000000-0000-4000-8000-000000000004', 'SDN302', 'SDN302 - Server-Side Development with NodeJS',
     'Tài liệu ôn thi SDN302.', 68000, 60, 157, 7424, 0, '#2563eb', 'on-thi',
     true, false, 40, 0, now(), now()),
    ('b1000000-0000-4000-8000-000000000005', 'MAI391', 'MAI391 - Mathematics for Engineering',
     'Tài liệu ôn thi MAI391.', 60000, 90, 320, 9100, 0, '#0e7490', 'on-thi',
     true, false, 50, 0, now(), now())
on conflict (id) do nothing;

insert into source_related_items (id, catalog_item_id, related_catalog_item_id, sort_order, created_at)
values
    ('b2000000-0000-4000-8000-000000000001', 'b1000000-0000-4000-8000-000000000001',
     'b1000000-0000-4000-8000-000000000002', 1, now()),
    ('b2000000-0000-4000-8000-000000000002', 'b1000000-0000-4000-8000-000000000001',
     'b1000000-0000-4000-8000-000000000003', 2, now()),
    ('b2000000-0000-4000-8000-000000000003', 'b1000000-0000-4000-8000-000000000002',
     'b1000000-0000-4000-8000-000000000001', 1, now())
on conflict (catalog_item_id, related_catalog_item_id) do nothing;
