package com.fuoverflow.payment.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class OrderEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "status", nullable = false, length = 32)
    private String status; // pending, paid, failed, refunded, cancelled

    @Column(name = "subtotal_cents", nullable = false)
    private int subtotalCents;

    @Column(name = "discount_cents", nullable = false)
    private int discountCents;

    @Column(name = "tax_cents", nullable = false)
    private int taxCents;

    @Column(name = "total_cents", nullable = false)
    private int totalCents;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "provider", length = 64)
    private String provider;

    @Column(name = "provider_order_id", length = 255)
    private String providerOrderId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderEntity() {
    }

    public static OrderEntity create(UUID userId, int totalCents, String currency, String provider, String providerOrderId) {
        OrderEntity order = new OrderEntity();
        order.id = UUID.randomUUID();
        order.userId = userId;
        order.status = "pending";
        order.subtotalCents = totalCents;
        order.discountCents = 0;
        order.taxCents = 0;
        order.totalCents = totalCents;
        order.currency = currency;
        order.provider = provider;
        order.providerOrderId = providerOrderId;
        order.lockVersion = 0;
        return order;
    }

    public void markAsPaid() {
        this.status = "paid";
    }

    public void markAsFailed() {
        this.status = "failed";
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getStatus() {
        return status;
    }

    public int getSubtotalCents() {
        return subtotalCents;
    }

    public int getDiscountCents() {
        return discountCents;
    }

    public int getTaxCents() {
        return taxCents;
    }

    public int getTotalCents() {
        return totalCents;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderOrderId() {
        return providerOrderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public int getLockVersion() {
        return lockVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
