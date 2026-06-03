package com.fuoverflow.forum.persistence;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id
    private UUID id;

    @Column(name = "forum_id", nullable = false)
    private UUID forumId;

    @Column(name = "slug", nullable = false, length = 120)
    private String slug;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 32)
    private ForumVisibility visibility;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected CategoryEntity() {
        // JPA requires no-arg constructor
    }

    public static CategoryEntity create(UUID id, UUID forumId, String slug, String title,
                                         String description, Integer sortOrder,
                                         ForumVisibility visibility, Instant now) {
        var entity = new CategoryEntity();
        entity.id = id;
        entity.forumId = forumId;
        entity.slug = slug;
        entity.title = title;
        entity.description = description;
        entity.sortOrder = sortOrder != null ? sortOrder : 0;
        entity.visibility = visibility != null ? visibility : ForumVisibility.PUBLIC;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void update(String title, String description, Integer sortOrder,
                       ForumVisibility visibility, Instant now) {
        if (title != null) {
            this.title = title;
        }
        if (description != null) {
            this.description = description;
        }
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
        if (visibility != null) {
            this.visibility = visibility;
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

    public UUID getForumId() {
        return forumId;
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

    public Integer getSortOrder() {
        return sortOrder;
    }

    public ForumVisibility getVisibility() {
        return visibility;
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
