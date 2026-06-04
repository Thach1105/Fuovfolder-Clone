-- Coursera performance checks (run against local/staging DB after V7 migration)
-- Expect Index Scan / Bitmap Index Scan on large tables, not Seq Scan on requests.

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM coursera_service_requests
ORDER BY created_at DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM coursera_service_requests
WHERE user_id = '00000000-0000-0000-0000-000000000001'
ORDER BY created_at DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT r.*
FROM coursera_service_requests r
WHERE r.id IN (
    SELECT i.request_id
    FROM coursera_request_items i
    WHERE i.catalog_item_id = '00000000-0000-0000-0000-000000000001'
)
ORDER BY r.created_at DESC
LIMIT 50;

EXPLAIN (ANALYZE, BUFFERS)
SELECT *
FROM coursera_catalog_items
WHERE deleted_at IS NULL
  AND is_active = true
ORDER BY sort_order ASC, title ASC;
