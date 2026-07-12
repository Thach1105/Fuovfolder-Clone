package com.fuoverflow.exam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_comments")
public class ExamCommentEntity {
    @Id
    private UUID id;

    /** 'fe_question' or 'pe_item'. */
    @Column(name = "subject_type", nullable = false, length = 16)
    private String subjectType;

    /** The fe_question id or pe_item id being commented on. */
    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    /** Denormalized exam_subjects.id for fast per-subject filtering/authz. */
    @Column(name = "exam_subject_id", nullable = false)
    private UUID examSubjectId;

    @Column(name = "author_user_id", nullable = false)
    private UUID authorUserId;

    @Column(name = "parent_comment_id")
    private UUID parentCommentId;

    @Column(name = "body_md", nullable = false, columnDefinition = "text")
    private String bodyMd;

    @Column(name = "body_html", nullable = false, columnDefinition = "text")
    private String bodyHtml;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "like_count", nullable = false)
    private int likeCount;

    public UUID getId() { return id; }
    public String getSubjectType() { return subjectType; }
    public UUID getSubjectId() { return subjectId; }
    public UUID getExamSubjectId() { return examSubjectId; }
    public UUID getAuthorUserId() { return authorUserId; }
    public UUID getParentCommentId() { return parentCommentId; }
    public String getBodyMd() { return bodyMd; }
    public String getBodyHtml() { return bodyHtml; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public int getLikeCount() { return likeCount; }

    public void setBodyMd(String bodyMd) { this.bodyMd = bodyMd; }
    public void setBodyHtml(String bodyHtml) { this.bodyHtml = bodyHtml; }
    public void setStatus(String status) { this.status = status; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
    public void setLikeCount(int likeCount) { this.likeCount = likeCount; }

    public static ExamCommentEntity create(
            UUID id, String subjectType, UUID subjectId, UUID examSubjectId,
            UUID authorUserId, UUID parentCommentId, String bodyMd, String bodyHtml, Instant now) {
        ExamCommentEntity e = new ExamCommentEntity();
        e.id = id;
        e.subjectType = subjectType;
        e.subjectId = subjectId;
        e.examSubjectId = examSubjectId;
        e.authorUserId = authorUserId;
        e.parentCommentId = parentCommentId;
        e.bodyMd = bodyMd;
        e.bodyHtml = bodyHtml;
        e.status = "visible";
        e.createdAt = now;
        e.updatedAt = now;
        e.likeCount = 0;
        return e;
    }
}
