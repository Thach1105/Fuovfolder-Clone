package com.fuoverflow.moderation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "moderation_actions")
public class ModerationActionEntity {
    @Id
    private UUID id;

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(columnDefinition = "text")
    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metadata_json", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadataJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getActorUserId() { return actorUserId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public Map<String, Object> getMetadataJson() { return metadataJson; }
    public Instant getCreatedAt() { return createdAt; }

    public static ModerationActionEntity create(
            UUID actorUserId,
            String action,
            String targetType,
            UUID targetId,
            String reason,
            Map<String, Object> metadataJson,
            Instant now) {
        ModerationActionEntity entity = new ModerationActionEntity();
        entity.id = UUID.randomUUID();
        entity.actorUserId = actorUserId;
        entity.action = action;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.reason = reason;
        entity.metadataJson = metadataJson == null ? Map.of() : metadataJson;
        entity.createdAt = now;
        return entity;
    }
}
