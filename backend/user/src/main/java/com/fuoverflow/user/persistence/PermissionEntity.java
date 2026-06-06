package com.fuoverflow.user.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "permissions")
public class PermissionEntity {
    @Id
    private String slug;

    @Column(nullable = false, length = 64)
    private String module;

    @Column(nullable = false, length = 64)
    private String resource;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(nullable = false)
    private String description;

    @Column(name = "is_system", nullable = false)
    private boolean system;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public String getSlug() { return slug; }
    public String getModule() { return module; }
    public String getResource() { return resource; }
    public String getAction() { return action; }
    public String getDescription() { return description; }
    public boolean isSystem() { return system; }
}
