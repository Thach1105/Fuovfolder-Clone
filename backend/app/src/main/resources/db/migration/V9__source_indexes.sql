-- Source: performance indexes for public catalog browse/sort and admin catalog listing.

-- Public catalog sort by newest
create index ix_source_catalog_active_created
    on source_catalog_items (is_active, created_at desc)
    where deleted_at is null;

-- Public catalog sort by price
create index ix_source_catalog_active_price
    on source_catalog_items (is_active, price_points)
    where deleted_at is null;

-- Public catalog sort by popularity
create index ix_source_catalog_active_views
    on source_catalog_items (is_active, view_count desc)
    where deleted_at is null;

-- Admin catalog list (non-deleted, default sort)
create index ix_source_catalog_live_sort
    on source_catalog_items (sort_order, title)
    where deleted_at is null;
