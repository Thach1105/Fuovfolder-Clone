insert into coursera_catalog_items (id, code, title, description, price_points, is_active, is_featured, sort_order, lock_version, created_at, updated_at)
values
    ('a1000000-0000-4000-8000-000000000001', 'WOU203C', 'User Experience Research and Design Specialization', 'Coursera UX Research and Design', 250000, true, true, 10, 0, now(), now()),
    ('a1000000-0000-4000-8000-000000000002', 'SSL101', 'Introduction to SSL/TLS Security', 'Security fundamentals specialization', 180000, true, true, 20, 0, now(), now()),
    ('a1000000-0000-4000-8000-000000000003', 'PYTHON-DATA', 'Python for Data Science and AI', 'Python data science track', 220000, true, false, 30, 0, now(), now()),
    ('a1000000-0000-4000-8000-000000000004', 'FER202', 'FER202 - Practical Exam Prep', 'Exam preparation package', 150000, true, false, 40, 0, now(), now()),
    ('a1000000-0000-4000-8000-000000000005', 'WED201C', 'WED201c - FROM ZERO TO HERO', 'Full stack learning path', 300000, true, true, 5, 0, now(), now())
on conflict (id) do nothing;
