package com.fuoverflow.user.persistence;

import com.fuoverflow.user.domain.PermissionEffect;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_permission_overrides")
public class UserPermissionOverrideEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "permission_slug", nullable = false, length = 128)
    private String permissionSlug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PermissionEffect effect;

    @Column
    private String reason;

    @Column(name = "assigned_by_user_id")
    private UUID assignedByUserId;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at")
    private Instant endsAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getPermissionSlug() { return permissionSlug; }
    public PermissionEffect getEffect() { return effect; }
    public String getReason() { return reason; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public void revoke(Instant at) { this.revokedAt = at; }

    public static UserPermissionOverrideEntity create(UUID id, UUID userId, String permissionSlug,
                                                      PermissionEffect effect, String reason,
                                                      UUID assignedBy, Instant now) {
        UserPermissionOverrideEntity entity = new UserPermissionOverrideEntity();
        entity.id = id;
        entity.userId = userId;
        entity.permissionSlug = permissionSlug;
        entity.effect = effect;
        entity.reason = reason;
        entity.assignedByUserId = assignedBy;
        entity.startsAt = now;
        entity.createdAt = now;
        return entity;
    }
}
