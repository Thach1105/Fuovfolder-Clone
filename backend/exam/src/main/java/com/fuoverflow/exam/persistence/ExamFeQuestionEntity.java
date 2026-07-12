package com.fuoverflow.exam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_fe_questions")
public class ExamFeQuestionEntity {
    @Id
    private UUID id;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_image_urls", columnDefinition = "jsonb")
    private String questionImageUrls;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "question_blur_urls", columnDefinition = "jsonb")
    private String questionBlurUrls;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

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
    public UUID getSubjectId() { return subjectId; }
    public String getQuestionText() { return questionText; }
    public String getQuestionImageUrls() { return questionImageUrls; }
    public String getQuestionBlurUrls() { return questionBlurUrls; }
    public int getSortOrder() { return sortOrder; }
    public int getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public void setQuestionImageUrls(String questionImageUrls) { this.questionImageUrls = questionImageUrls; }
    public void setQuestionBlurUrls(String questionBlurUrls) { this.questionBlurUrls = questionBlurUrls; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static ExamFeQuestionEntity create(
            UUID id, UUID subjectId, String questionText,
            String questionImageUrls, String questionBlurUrls,
            int sortOrder, Instant now) {
        ExamFeQuestionEntity e = new ExamFeQuestionEntity();
        e.id = id;
        e.subjectId = subjectId;
        e.questionText = questionText;
        e.questionImageUrls = questionImageUrls;
        e.questionBlurUrls = questionBlurUrls;
        e.sortOrder = sortOrder;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
