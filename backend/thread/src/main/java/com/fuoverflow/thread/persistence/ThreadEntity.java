package com.fuoverflow.thread.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "threads")
public class ThreadEntity {
    @Id
    private UUID id;

    @Column(name = "forum_id", nullable = false)
    private UUID forumId;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 350)
    private String slug;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "pinned_at")
    private Instant pinnedAt;

    @Column(name = "locked_at")
    private Instant lockedAt;

    @Column(name = "last_post_id")
    private UUID lastPostId;

    @Column(name = "last_post_at", nullable = false)
    private Instant lastPostAt;

    @Column(name = "reply_count", nullable = false)
    private int replyCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "reaction_count", nullable = false)
    private int reactionCount;

    @Column(name = "imported_author_handle", length = 190)
    private String importedAuthorHandle;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getForumId() { return forumId; }
    public UUID getCategoryId() { return categoryId; }
    public UUID getAuthorUserId() { return authorUserId; }
    public String getTitle() { return title; }
    public String getSlug() { return slug; }
    public String getStatus() { return status; }
    public Instant getPinnedAt() { return pinnedAt; }
    public Instant getLockedAt() { return lockedAt; }
    public UUID getLastPostId() { return lastPostId; }
    public Instant getLastPostAt() { return lastPostAt; }
    public int getReplyCount() { return replyCount; }
    public long getViewCount() { return viewCount; }
    public int getReactionCount() { return reactionCount; }
    public String getImportedAuthorHandle() { return importedAuthorHandle; }
    public String getSourceUrl() { return sourceUrl; }
    public int getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setForumId(UUID forumId) { this.forumId = forumId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }
    public void setAuthorUserId(UUID authorUserId) { this.authorUserId = authorUserId; }
    public void setTitle(String title) { this.title = title; }
    public void setSlug(String slug) { this.slug = slug; }
    public void setStatus(String status) { this.status = status; }
    public void setPinnedAt(Instant pinnedAt) { this.pinnedAt = pinnedAt; }
    public void setLockedAt(Instant lockedAt) { this.lockedAt = lockedAt; }
    public void setLastPostId(UUID lastPostId) { this.lastPostId = lastPostId; }
    public void setLastPostAt(Instant lastPostAt) { this.lastPostAt = lastPostAt; }
    public void setReplyCount(int replyCount) { this.replyCount = replyCount; }
    public void setViewCount(long viewCount) { this.viewCount = viewCount; }
    public void setReactionCount(int reactionCount) { this.reactionCount = reactionCount; }
    public void setImportedAuthorHandle(String importedAuthorHandle) { this.importedAuthorHandle = importedAuthorHandle; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static ThreadEntity createImported(
            UUID id, UUID forumId, UUID categoryId, UUID authorUserId,
            String title, String slug, Instant lastPostAt, Instant now) {
        ThreadEntity e = new ThreadEntity();
        e.id = id;
        e.forumId = forumId;
        e.categoryId = categoryId;
        e.authorUserId = authorUserId;
        e.title = title;
        e.slug = slug;
        e.status = "open";
        e.lastPostAt = lastPostAt != null ? lastPostAt : now;
        e.replyCount = 0;
        e.viewCount = 0L;
        e.reactionCount = 0;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
