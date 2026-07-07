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

    @Column(columnDefinition = "text")
    private String explanation;

    @Column(name = "multiple_correct", nullable = false)
    private boolean multipleCorrect;

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
    public String getExplanation() { return explanation; }
    public boolean isMultipleCorrect() { return multipleCorrect; }
    public int getSortOrder() { return sortOrder; }
    public int getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public void setQuestionImageUrls(String questionImageUrls) { this.questionImageUrls = questionImageUrls; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public void setMultipleCorrect(boolean multipleCorrect) { this.multipleCorrect = multipleCorrect; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static ExamFeQuestionEntity create(
            UUID id, UUID subjectId, String questionText, String questionImageUrls,
            String explanation, boolean multipleCorrect, int sortOrder, Instant now) {
        ExamFeQuestionEntity e = new ExamFeQuestionEntity();
        e.id = id;
        e.subjectId = subjectId;
        e.questionText = questionText;
        e.questionImageUrls = questionImageUrls;
        e.explanation = explanation;
        e.multipleCorrect = multipleCorrect;
        e.sortOrder = sortOrder;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
