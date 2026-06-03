package com.fuoverflow.forum.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forums")
public class ForumEntity {
    @Id
    private UUID id;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private ForumVisibility visibility;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected ForumEntity() {
        // JPA requires no-arg constructor
    }

    public static ForumEntity create(UUID id, String slug, String title, String description,
                                      ForumVisibility visibility, Integer sortOrder,
                                      UUID createdByUserId, Instant now) {
        var entity = new ForumEntity();
        entity.id = id;
        entity.slug = slug;
        entity.title = title;
        entity.description = description;
        entity.visibility = visibility != null ? visibility : ForumVisibility.PUBLIC;
        entity.sortOrder = sortOrder != null ? sortOrder : 0;
        entity.createdByUserId = createdByUserId;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(String title, String description, ForumVisibility visibility,
                       Integer sortOrder, Instant now) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (visibility != null) {
            this.visibility = visibility;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        this.updatedAt = now;
    }

    public void softDelete(Instant now) {
        this.deletedAt = now;
        this.updatedAt = now;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isPublic() {
        return visibility == ForumVisibility.PUBLIC;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public ForumVisibility getVisibility() {
        return visibility;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }
}
