package com.fuoverflow.forum.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "categories")
public class CategoryEntity {
    @Id
    private UUID id;

    @Column(name = "forum_id", nullable = false)
    private UUID forumId;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(nullable = false, length = 32)
    private String visibility;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getForumId() { return forumId; }
    public String getSlug() { return slug; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public int getSortOrder() { return sortOrder; }
    public String getVisibility() { return visibility; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setForumId(UUID forumId) { this.forumId = forumId; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setVisibility(String visibility) { this.visibility = visibility; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static CategoryEntity createImported(UUID id, UUID forumId, String slug, String title, Instant now) {
        CategoryEntity e = new CategoryEntity();
        e.id = id;
        e.forumId = forumId;
        e.slug = slug;
        e.title = title;
        e.visibility = "public";
        e.sortOrder = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
