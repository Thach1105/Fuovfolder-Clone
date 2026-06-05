package com.fuoverflow.notification.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "push_subscriptions")
public class PushSubscriptionEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(name = "auth_key", nullable = false, length = 255)
    private String authKey;

    @Column(name = "user_agent", columnDefinition = "text")
    private String userAgent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getEndpoint() { return endpoint; }
    public String getP256dh() { return p256dh; }
    public String getAuthKey() { return authKey; }

    public static PushSubscriptionEntity create(
            UUID id,
            UUID userId,
            String endpoint,
            String p256dh,
            String authKey,
            String userAgent,
            Instant now) {
        PushSubscriptionEntity entity = new PushSubscriptionEntity();
        entity.id = id;
        entity.userId = userId;
        entity.endpoint = endpoint;
        entity.p256dh = p256dh;
        entity.authKey = authKey;
        entity.userAgent = userAgent;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void refresh(String p256dh, String authKey, String userAgent, Instant now) {
        this.p256dh = p256dh;
        this.authKey = authKey;
        this.userAgent = userAgent;
        this.updatedAt = now;
    }
}
