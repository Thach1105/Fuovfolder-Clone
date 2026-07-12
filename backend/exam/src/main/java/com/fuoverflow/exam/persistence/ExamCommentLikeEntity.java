package com.fuoverflow.exam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_comment_likes")
public class ExamCommentLikeEntity {
    @Id
    private UUID id;

    @Column(name = "comment_id", nullable = false)
    private UUID commentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getCommentId() { return commentId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }

    public static ExamCommentLikeEntity create(UUID id, UUID commentId, UUID userId, Instant now) {
        ExamCommentLikeEntity e = new ExamCommentLikeEntity();
        e.id = id;
        e.commentId = commentId;
        e.userId = userId;
        e.createdAt = now;
        return e;
    }
}
