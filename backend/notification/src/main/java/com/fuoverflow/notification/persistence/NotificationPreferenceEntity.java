package com.fuoverflow.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notification_preferences")
public class NotificationPreferenceEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String channel;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getUserId() { return userId; }
    public String getChannel() { return channel; }
    public String getType() { return type; }
    public boolean isEnabled() { return enabled; }

    public UUID getId() { return id; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static NotificationPreferenceEntity create(
            UUID id, UUID userId, String channel, String type, boolean enabled, Instant now) {
        NotificationPreferenceEntity entity = new NotificationPreferenceEntity();
        entity.id = id;
        entity.userId = userId;
        entity.channel = channel;
        entity.type = type;
        entity.enabled = enabled;
        entity.updatedAt = now;
        return entity;
    }
}
