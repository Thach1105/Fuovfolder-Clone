package com.fuoverflow.award.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "points_ledger")
public class PointsLedgerEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private String reason;

    @Column(name = "source_type", nullable = false, length = 32)
    private String sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static PointsLedgerEntity entry(UUID id, UUID userId, int delta, String reason, String sourceType, UUID sourceId, Instant at) {
        PointsLedgerEntity e = new PointsLedgerEntity();
        e.id = id;
        e.userId = userId;
        e.delta = delta;
        e.reason = reason;
        e.sourceType = sourceType;
        e.sourceId = sourceId;
        e.createdAt = at;
        return e;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public int getDelta() {
        return delta;
    }

    public String getReason() {
        return reason;
    }

    public String getSourceType() {
        return sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
