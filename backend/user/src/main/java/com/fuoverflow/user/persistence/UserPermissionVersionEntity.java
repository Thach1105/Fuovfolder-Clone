package com.fuoverflow.user.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_permission_versions")
public class UserPermissionVersionEntity {
    @Id
    private UUID userId;

    @Column(nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getUserId() { return userId; }
    public long getVersion() { return version; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void bump(Instant at) {
        this.version++;
        this.updatedAt = at;
    }

    public static UserPermissionVersionEntity initial(UUID userId, Instant at) {
        UserPermissionVersionEntity entity = new UserPermissionVersionEntity();
        entity.userId = userId;
        entity.version = 1;
        entity.updatedAt = at;
        return entity;
    }
}
