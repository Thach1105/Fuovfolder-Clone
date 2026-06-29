package com.fuoverflow.broadcast.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "broadcast_configs")
public class BroadcastConfigEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "event_type", nullable = false, unique = true, length = 64)
    private String eventType;

    @Column(name = "config_json", nullable = false, columnDefinition = "jsonb")
    private String configJson;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected BroadcastConfigEntity() {}

    public static BroadcastConfigEntity create(String eventType, String configJson) {
        BroadcastConfigEntity e = new BroadcastConfigEntity();
        e.eventType = eventType;
        e.configJson = configJson;
        e.enabled = true;
        e.createdAt = Instant.now();
        e.updatedAt = Instant.now();
        return e;
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getConfigJson() { return configJson; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateConfig(String configJson, boolean enabled) {
        this.configJson = configJson;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }
}
