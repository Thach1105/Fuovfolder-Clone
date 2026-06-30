package com.fuoverflow.voucher.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voucher_redemptions")
public class VoucherRedemptionEntity {
    @Id
    private UUID id;

    @Column(name = "voucher_id", nullable = false)
    private UUID voucherId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "transaction_type", nullable = false, length = 32)
    private String transactionType;

    @Column(name = "transaction_id", nullable = false)
    private UUID transactionId;

    @Column(name = "original_points", nullable = false)
    private int originalPoints;

    @Column(name = "discount_points", nullable = false)
    private int discountPoints;

    @Column(name = "final_points", nullable = false)
    private int finalPoints;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getVoucherId() { return voucherId; }
    public UUID getUserId() { return userId; }
    public String getTransactionType() { return transactionType; }
    public UUID getTransactionId() { return transactionId; }
    public int getOriginalPoints() { return originalPoints; }
    public int getDiscountPoints() { return discountPoints; }
    public int getFinalPoints() { return finalPoints; }
    public Instant getCreatedAt() { return createdAt; }

    public static VoucherRedemptionEntity create(
            UUID id, UUID voucherId, UUID userId, String transactionType,
            UUID transactionId, int originalPoints, int discountPoints,
            int finalPoints, Instant now) {
        VoucherRedemptionEntity e = new VoucherRedemptionEntity();
        e.id = id;
        e.voucherId = voucherId;
        e.userId = userId;
        e.transactionType = transactionType;
        e.transactionId = transactionId;
        e.originalPoints = originalPoints;
        e.discountPoints = discountPoints;
        e.finalPoints = finalPoints;
        e.createdAt = now;
        return e;
    }
}
