package com.fuoverflow.deposit.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deposit_tiers")
public class DepositTierEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(name = "amount_vnd", nullable = false)
    private int amountVnd;

    @Column(nullable = false)
    private int points;

    @Column(name = "bonus_percent", nullable = false)
    private int bonusPercent;

    @Column(name = "is_active", nullable = false)
    private boolean isActive;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DepositTierEntity() {
    }

    public static DepositTierEntity create(String label, int amountVnd, int points,
                                           int bonusPercent, boolean active, int sortOrder,
                                           Instant now) {
        DepositTierEntity e = new DepositTierEntity();
        e.id = UUID.randomUUID();
        e.label = label;
        e.amountVnd = amountVnd;
        e.points = points;
        e.bonusPercent = bonusPercent;
        e.isActive = active;
        e.sortOrder = sortOrder;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }

    public void update(String label, int amountVnd, int points, int bonusPercent,
                       boolean active, int sortOrder, Instant now) {
        this.label = label;
        this.amountVnd = amountVnd;
        this.points = points;
        this.bonusPercent = bonusPercent;
        this.isActive = active;
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    public long totalPoints() {
        return (long) points + ((long) points * bonusPercent / 100L);
    }

    public UUID getId() { return id; }
    public String getLabel() { return label; }
    public int getAmountVnd() { return amountVnd; }
    public int getPoints() { return points; }
    public int getBonusPercent() { return bonusPercent; }
    public boolean isActive() { return isActive; }
    public int getSortOrder() { return sortOrder; }
    public int getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
