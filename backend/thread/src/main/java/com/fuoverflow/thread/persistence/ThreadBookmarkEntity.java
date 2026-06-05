package com.fuoverflow.thread.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "thread_bookmarks")
public class ThreadBookmarkEntity {
    @Id
    private UUID id;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getThreadId() { return threadId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }

    public static ThreadBookmarkEntity create(UUID id, UUID threadId, UUID userId, Instant now) {
        ThreadBookmarkEntity entity = new ThreadBookmarkEntity();
        entity.id = id;
        entity.threadId = threadId;
        entity.userId = userId;
        entity.createdAt = now;
        return entity;
    }
}
