package com.fuoverflow.source.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_purchases")
public class SourcePurchaseEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "code_snapshot", nullable = false, length = 64)
    private String codeSnapshot;

    @Column(name = "title_snapshot", nullable = false, length = 500)
    private String titleSnapshot;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "unit_price_points", nullable = false)
    private int unitPricePoints;

    @Column(name = "access_days_snapshot", nullable = false)
    private int accessDaysSnapshot;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(name = "payment_ledger_id")
    private UUID paymentLedgerId;

    @Column(name = "refund_ledger_id")
    private UUID refundLedgerId;

    @Column(name = "refund_reason", length = 500)
    private String refundReason;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getCatalogItemId() { return catalogItemId; }
    public String getCodeSnapshot() { return codeSnapshot; }
    public String getTitleSnapshot() { return titleSnapshot; }
    public String getStatus() { return status; }
    public int getUnitPricePoints() { return unitPricePoints; }
    public int getAccessDaysSnapshot() { return accessDaysSnapshot; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public UUID getPaymentLedgerId() { return paymentLedgerId; }
    public UUID getRefundLedgerId() { return refundLedgerId; }
    public String getRefundReason() { return refundReason; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setPaymentLedgerId(UUID paymentLedgerId) { this.paymentLedgerId = paymentLedgerId; }
    public void setRefundLedgerId(UUID refundLedgerId) { this.refundLedgerId = refundLedgerId; }
    public void setRefundReason(String refundReason) { this.refundReason = refundReason; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static SourcePurchaseEntity createActive(
            UUID id, UUID userId, UUID catalogItemId, String codeSnapshot, String titleSnapshot,
            int unitPricePoints, int accessDaysSnapshot, Instant startsAt, Instant endsAt,
            String idempotencyKey, Instant now) {
        SourcePurchaseEntity e = new SourcePurchaseEntity();
        e.id = id;
        e.userId = userId;
        e.catalogItemId = catalogItemId;
        e.codeSnapshot = codeSnapshot;
        e.titleSnapshot = titleSnapshot;
        e.status = "active";
        e.unitPricePoints = unitPricePoints;
        e.accessDaysSnapshot = accessDaysSnapshot;
        e.startsAt = startsAt;
        e.endsAt = endsAt;
        e.idempotencyKey = idempotencyKey;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
