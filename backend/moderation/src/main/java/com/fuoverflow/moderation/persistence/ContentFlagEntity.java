package com.fuoverflow.moderation.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "content_flags")
public class ContentFlagEntity {
    @Id
    private UUID id;

    @Column(name = "reporter_user_id", nullable = false)
    private UUID reporterUserId;

    @Column(name = "target_type", nullable = false, length = 32)
    private String targetType;

    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(nullable = false, length = 120)
    private String reason;

    @Column(columnDefinition = "text")
    private String note;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "resolver_user_id")
    private UUID resolverUserId;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getReporterUserId() { return reporterUserId; }
    public String getTargetType() { return targetType; }
    public UUID getTargetId() { return targetId; }
    public String getReason() { return reason; }
    public String getNote() { return note; }
    public String getStatus() { return status; }
    public UUID getResolverUserId() { return resolverUserId; }
    public Instant getResolvedAt() { return resolvedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
    public void setResolverUserId(UUID resolverUserId) { this.resolverUserId = resolverUserId; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    public static ContentFlagEntity create(
            UUID reporterUserId, String targetType, UUID targetId, String reason, String note, Instant now) {
        ContentFlagEntity entity = new ContentFlagEntity();
        entity.id = UUID.randomUUID();
        entity.reporterUserId = reporterUserId;
        entity.targetType = targetType;
        entity.targetId = targetId;
        entity.reason = reason;
        entity.note = note;
        entity.status = "open";
        entity.createdAt = now;
        return entity;
    }
}
