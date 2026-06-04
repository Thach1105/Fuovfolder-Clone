-- Coursera: performance indexes for admin/user list, catalog filter, and catalog browse

-- Admin default sort + date-range scans
create index ix_coursera_requests_created_desc
    on coursera_service_requests (created_at desc);

-- Admin/user filter by customer + time
create index ix_coursera_requests_user_status_created
    on coursera_service_requests (user_id, status, created_at desc);

-- Admin filter by course (subquery / join on request_items)
create index ix_coursera_request_items_catalog_request
    on coursera_request_items (catalog_item_id, request_id);

-- Public catalog: featured block + sort
create index ix_coursera_catalog_active_featured_sort
    on coursera_catalog_items (is_active, is_featured, sort_order)
    where deleted_at is null;

-- Admin catalog list (non-deleted, sort)
create index ix_coursera_catalog_live_sort
    on coursera_catalog_items (sort_order, title)
    where deleted_at is null;
