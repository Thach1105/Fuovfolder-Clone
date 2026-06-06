package com.fuoverflow.user.persistence;

import com.fuoverflow.user.domain.RoleType;
import com.fuoverflow.user.support.PermissionJson;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class RoleEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String slug;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 32)
    private String scope;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "permissions_json", columnDefinition = "jsonb", nullable = false)
    private String permissionsJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "role_type", nullable = false, length = 32)
    private RoleType roleType;

    @Column(name = "parent_role_id")
    private UUID parentRoleId;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "is_editable", nullable = false)
    private boolean editable;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getScope() { return scope; }
    public String getPermissionsJson() { return permissionsJson; }
    public RoleType getRoleType() { return roleType; }
    public UUID getParentRoleId() { return parentRoleId; }
    public boolean isSystem() { return system; }
    public boolean isEditable() { return editable; }

    public void setName(String name) { this.name = name; }
    public void setPermissionsJson(String permissionsJson) { this.permissionsJson = permissionsJson; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static RoleEntity createCustom(UUID id, String slug, String name, RoleType roleType,
                                          UUID parentRoleId, List<String> permissions, Instant now) {
        RoleEntity entity = new RoleEntity();
        entity.id = id;
        entity.slug = slug;
        entity.name = name;
        entity.scope = "global";
        entity.permissionsJson = PermissionJson.toJson(permissions);
        entity.roleType = roleType;
        entity.parentRoleId = parentRoleId;
        entity.system = false;
        entity.editable = true;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
}
