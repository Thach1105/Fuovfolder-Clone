package com.fuoverflow.membership.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "memberships")
public class MembershipEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "cancel_at")
    private Instant cancelAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public UUID getPlanId() { return planId; }
    public String getStatus() { return status; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getCurrentPeriodStart() { return currentPeriodStart; }
    public Instant getCurrentPeriodEnd() { return currentPeriodEnd; }
    public Instant getCancelledAt() { return cancelledAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setCurrentPeriodEnd(Instant currentPeriodEnd) { this.currentPeriodEnd = currentPeriodEnd; }
    public void setCancelledAt(Instant cancelledAt) { this.cancelledAt = cancelledAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static MembershipEntity create(UUID id, UUID userId, UUID planId, Instant startsAt, Instant endsAt, Instant now) {
        MembershipEntity entity = new MembershipEntity();
        entity.id = id;
        entity.userId = userId;
        entity.planId = planId;
        entity.status = "active";
        entity.startsAt = startsAt;
        entity.currentPeriodStart = startsAt;
        entity.currentPeriodEnd = endsAt;
        entity.lockVersion = 0;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
}
