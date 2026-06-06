package com.fuoverflow.user.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "role_assignments")
public class RoleAssignmentEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    @Column(name = "scope_type", nullable = false, length = 32)
    private String scopeType;

    @Column(name = "scope_id")
    private UUID scopeId;

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
    public UUID getRoleId() { return roleId; }
    public String getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public void revoke(Instant at) { this.revokedAt = at; }

    public static RoleAssignmentEntity create(UUID id, UUID userId, UUID roleId, UUID assignedBy, Instant now) {
        RoleAssignmentEntity entity = new RoleAssignmentEntity();
        entity.id = id;
        entity.userId = userId;
        entity.roleId = roleId;
        entity.scopeType = "global";
        entity.scopeId = null;
        entity.assignedByUserId = assignedBy;
        entity.startsAt = now;
        entity.createdAt = now;
        return entity;
    }
}
