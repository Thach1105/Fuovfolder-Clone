package com.fuoverflow.source.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_related_items")
public class SourceRelatedItemEntity {
    @Id
    private UUID id;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "related_catalog_item_id", nullable = false)
    private UUID relatedCatalogItemId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public UUID getRelatedCatalogItemId() { return relatedCatalogItemId; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }

    public static SourceRelatedItemEntity create(
            UUID id, UUID catalogItemId, UUID relatedCatalogItemId, int sortOrder, Instant now) {
        SourceRelatedItemEntity e = new SourceRelatedItemEntity();
        e.id = id;
        e.catalogItemId = catalogItemId;
        e.relatedCatalogItemId = relatedCatalogItemId;
        e.sortOrder = sortOrder;
        e.createdAt = now;
        return e;
    }
}
