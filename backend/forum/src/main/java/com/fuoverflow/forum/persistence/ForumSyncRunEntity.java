package com.fuoverflow.forum.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "forum_sync_runs")
public class ForumSyncRunEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 32)
    private String mode;

    @Column(nullable = false, length = 32)
    private String scope;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "forums_synced", nullable = false)
    private int forumsSynced;

    @Column(name = "threads_synced", nullable = false)
    private int threadsSynced;

    @Column(name = "posts_synced", nullable = false)
    private int postsSynced;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public String getMode() { return mode; }
    public String getScope() { return scope; }
    public String getStatus() { return status; }
    public int getForumsSynced() { return forumsSynced; }
    public int getThreadsSynced() { return threadsSynced; }
    public int getPostsSynced() { return postsSynced; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(String status) { this.status = status; }
    public void setForumsSynced(int forumsSynced) { this.forumsSynced = forumsSynced; }
    public void setThreadsSynced(int threadsSynced) { this.threadsSynced = threadsSynced; }
    public void setPostsSynced(int postsSynced) { this.postsSynced = postsSynced; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setFinishedAt(Instant finishedAt) { this.finishedAt = finishedAt; }

    public static ForumSyncRunEntity start(UUID id, String mode, String scope, Instant now) {
        ForumSyncRunEntity e = new ForumSyncRunEntity();
        e.id = id;
        e.mode = mode;
        e.scope = scope;
        e.status = "running";
        e.startedAt = now;
        e.createdAt = now;
        return e;
    }
}
