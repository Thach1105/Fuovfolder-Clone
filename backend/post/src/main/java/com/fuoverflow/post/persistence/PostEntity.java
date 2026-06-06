package com.fuoverflow.post.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "posts")
public class PostEntity {
    @Id
    private UUID id;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "parent_post_id")
    private UUID parentPostId;

    @Column(name = "body_md", nullable = false, columnDefinition = "text")
    private String bodyMd;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "edit_count", nullable = false)
    private int editCount;

    @Column(name = "edit_version", nullable = false)
    private int editVersion;

    @Column(name = "reaction_count", nullable = false)
    private int reactionCount;

    @Column(name = "last_edited_at")
    private Instant lastEditedAt;

    @Column(name = "imported_author_handle", length = 190)
    private String importedAuthorHandle;

    @Column(name = "source_url", columnDefinition = "text")
    private String sourceUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getThreadId() { return threadId; }
    public UUID getAuthorUserId() { return authorUserId; }
    public UUID getParentPostId() { return parentPostId; }
    public String getBodyMd() { return bodyMd; }
    public String getBodyHtml() { return bodyHtml; }
    public String getStatus() { return status; }
    public int getEditCount() { return editCount; }
    public int getEditVersion() { return editVersion; }
    public int getReactionCount() { return reactionCount; }
    public Instant getLastEditedAt() { return lastEditedAt; }
    public String getImportedAuthorHandle() { return importedAuthorHandle; }
    public String getSourceUrl() { return sourceUrl; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setThreadId(UUID threadId) { this.threadId = threadId; }
    public void setAuthorUserId(UUID authorUserId) { this.authorUserId = authorUserId; }
    public void setParentPostId(UUID parentPostId) { this.parentPostId = parentPostId; }
    public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
    public void setStatus(String status) { this.status = status; }
    public void setEditCount(int editCount) { this.editCount = editCount; }
    public void setEditVersion(int editVersion) { this.editVersion = editVersion; }
    public void setReactionCount(int reactionCount) { this.reactionCount = reactionCount; }
    public void setLastEditedAt(Instant lastEditedAt) { this.lastEditedAt = lastEditedAt; }
    public void setImportedAuthorHandle(String importedAuthorHandle) { this.importedAuthorHandle = importedAuthorHandle; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static PostEntity createImported(
            UUID id, UUID threadId, UUID authorUserId,
            String bodyMd, String bodyHtml, Instant createdAt, Instant now) {
        PostEntity e = new PostEntity();
        e.id = id;
        e.threadId = threadId;
        e.authorUserId = authorUserId;
        e.bodyMd = bodyMd;
        e.bodyHtml = bodyHtml;
        e.status = "visible";
        e.editCount = 0;
        e.editVersion = 1;
        e.reactionCount = 0;
        e.createdAt = createdAt != null ? createdAt : now;
        e.updatedAt = now;
        return e;
    }

    public static PostEntity createUserPost(
            UUID id,
            UUID threadId,
            UUID authorUserId,
            UUID parentPostId,
            String authorHandle,
            String bodyMd,
            String bodyHtml,
            String status,
            Instant now) {
        PostEntity e = new PostEntity();
        e.id = id;
        e.threadId = threadId;
        e.authorUserId = authorUserId;
        e.parentPostId = parentPostId;
        e.bodyMd = bodyMd;
        e.bodyHtml = bodyHtml;
        e.status = status;
        e.editCount = 0;
        e.editVersion = 1;
        e.reactionCount = 0;
        e.importedAuthorHandle = authorHandle;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
