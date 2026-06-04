package com.fuoverflow.coursera.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coursera_request_items")
public class CourseraRequestItemEntity {
    @Id
    private UUID id;

    @Column(name = "request_id", nullable = false)
    private UUID requestId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "item_title_snapshot", nullable = false, length = 500)
    private String itemTitleSnapshot;

    @Column(name = "unit_price_points", nullable = false)
    private int unitPricePoints;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getRequestId() { return requestId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public String getItemTitleSnapshot() { return itemTitleSnapshot; }
    public int getUnitPricePoints() { return unitPricePoints; }
    public int getQuantity() { return quantity; }

    public static CourseraRequestItemEntity create(
            UUID id, UUID requestId, UUID catalogItemId, String titleSnapshot, int unitPrice, int quantity, Instant now) {
        CourseraRequestItemEntity e = new CourseraRequestItemEntity();
        e.id = id;
        e.requestId = requestId;
        e.catalogItemId = catalogItemId;
        e.itemTitleSnapshot = titleSnapshot;
        e.unitPricePoints = unitPrice;
        e.quantity = quantity;
        e.createdAt = now;
        return e;
    }
}
