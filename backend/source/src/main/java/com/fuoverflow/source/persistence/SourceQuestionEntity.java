package com.fuoverflow.source.persistence;

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
@Table(name = "source_questions")
public class SourceQuestionEntity {
    @Id
    private UUID id;

    @Column(name = "catalog_item_id", nullable = false)
    private UUID catalogItemId;

    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @Column(name = "question_image_url", length = 500)
    private String questionImageUrl;

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
    public UUID getCatalogItemId() { return catalogItemId; }
    public String getQuestionText() { return questionText; }
    public String getQuestionImageUrl() { return questionImageUrl; }
    public String getQuestionImageUrls() { return questionImageUrls; }
    public String getExplanation() { return explanation; }
    public boolean isMultipleCorrect() { return multipleCorrect; }
    public int getSortOrder() { return sortOrder; }
    public int getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setQuestionText(String questionText) { this.questionText = questionText; }
    public void setQuestionImageUrl(String questionImageUrl) { this.questionImageUrl = questionImageUrl; }
    public void setQuestionImageUrls(String questionImageUrls) { this.questionImageUrls = questionImageUrls; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public void setMultipleCorrect(boolean multipleCorrect) { this.multipleCorrect = multipleCorrect; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static SourceQuestionEntity create(
            UUID id,
            UUID catalogItemId,
            String questionText,
            String questionImageUrl,
            String questionImageUrls,
            String explanation,
            boolean multipleCorrect,
            int sortOrder,
            Instant now) {
        SourceQuestionEntity e = new SourceQuestionEntity();
        e.id = id;
        e.catalogItemId = catalogItemId;
        e.questionText = questionText;
        e.questionImageUrl = questionImageUrl;
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
