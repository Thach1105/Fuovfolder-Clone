package com.fuoverflow.reaction.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_reactions")
public class PostReactionEntity {
    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "reaction_type", nullable = false, length = 32)
    private String reactionType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getUserId() { return userId; }
    public String getReactionType() { return reactionType; }
    public Instant getCreatedAt() { return createdAt; }

    public static PostReactionEntity create(UUID postId, UUID userId, String reactionType, Instant now) {
        PostReactionEntity entity = new PostReactionEntity();
        entity.id = UUID.randomUUID();
        entity.postId = postId;
        entity.userId = userId;
        entity.reactionType = reactionType;
        entity.createdAt = now;
        return entity;
    }
}
