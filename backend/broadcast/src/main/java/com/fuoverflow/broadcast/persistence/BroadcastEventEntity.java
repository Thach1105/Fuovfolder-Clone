package com.fuoverflow.broadcast.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "broadcast_events")
public class BroadcastEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @Column(name = "data_json", nullable = false, columnDefinition = "jsonb")
    private String dataJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BroadcastEventEntity() {}

    public static BroadcastEventEntity create(String eventType, String message, String dataJson) {
        BroadcastEventEntity e = new BroadcastEventEntity();
        e.eventType = eventType;
        e.message = message;
        e.dataJson = dataJson;
        e.createdAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getMessage() { return message; }
    public String getDataJson() { return dataJson; }
    public Instant getCreatedAt() { return createdAt; }
}
