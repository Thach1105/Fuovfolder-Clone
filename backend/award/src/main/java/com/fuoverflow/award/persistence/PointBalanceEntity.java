package com.fuoverflow.award.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_balances")
public class PointBalanceEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "balance_points", nullable = false)
    private long balancePoints;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PointBalanceEntity() {}

    public static PointBalanceEntity create(UUID userId) {
        PointBalanceEntity e = new PointBalanceEntity();
        e.id = UUID.randomUUID();
        e.userId = userId;
        e.balancePoints = 0L;
        return e;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public long getBalancePoints() { return balancePoints; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void adjustBalance(int delta) {
        this.balancePoints += delta;
    }

    public void setBalancePoints(long points) {
        this.balancePoints = points;
    }
}
