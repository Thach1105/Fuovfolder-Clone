package com.fuoverflow.forum.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "poll_options")
public class PollOptionEntity {
    @Id
    private UUID id;

    @Column(name = "thread_id", nullable = false)
    private UUID threadId;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(name = "vote_count", nullable = false)
    private int voteCount;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public UUID getThreadId() { return threadId; }
    public String getLabel() { return label; }
    public int getVoteCount() { return voteCount; }
    public int getSortOrder() { return sortOrder; }

    public static PollOptionEntity create(UUID id, UUID threadId, String label, int sortOrder, Instant now) {
        PollOptionEntity e = new PollOptionEntity();
        e.id = id;
        e.threadId = threadId;
        e.label = label;
        e.voteCount = 0;
        e.sortOrder = sortOrder;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
