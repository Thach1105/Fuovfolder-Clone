package com.fuoverflow.source.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_purchase_events")
public class SourcePurchaseEventEntity {
    @Id
    private UUID id;

    @Column(name = "purchase_id", nullable = false)
    private UUID purchaseId;

    @Column(name = "event_type", nullable = false, length = 32)
    private String eventType;

    @Column(name = "from_status", length = 32)
    private String fromStatus;

    @Column(name = "to_status", length = 32)
    private String toStatus;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getPurchaseId() { return purchaseId; }
    public String getEventType() { return eventType; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public UUID getActorUserId() { return actorUserId; }
    public String getNote() { return note; }
    public Instant getCreatedAt() { return createdAt; }

    public static SourcePurchaseEventEntity create(
            UUID id, UUID purchaseId, String eventType, String fromStatus, String toStatus,
            UUID actorUserId, String note, Instant now) {
        SourcePurchaseEventEntity e = new SourcePurchaseEventEntity();
        e.id = id;
        e.purchaseId = purchaseId;
        e.eventType = eventType;
        e.fromStatus = fromStatus;
        e.toStatus = toStatus;
        e.actorUserId = actorUserId;
        e.note = note;
        e.createdAt = now;
        return e;
    }
}
