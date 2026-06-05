package com.fuoverflow.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class NotificationEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 64)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String body;

    @Column(name = "data_json", nullable = false, columnDefinition = "jsonb")
    private String dataJson;

    @Column(name = "read_at")
    private Instant readAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getType() { return type; }
    public String getTitle() { return title; }
    public String getBody() { return body; }
    public String getDataJson() { return dataJson; }
    public Instant getReadAt() { return readAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setReadAt(Instant readAt) { this.readAt = readAt; }

    public static NotificationEntity create(
            UUID id,
            UUID userId,
            String type,
            String title,
            String body,
            String dataJson,
            Instant now) {
        NotificationEntity entity = new NotificationEntity();
        entity.id = id;
        entity.userId = userId;
        entity.type = type;
        entity.title = title;
        entity.body = body;
        entity.dataJson = dataJson;
        entity.createdAt = now;
        return entity;
    }
}
