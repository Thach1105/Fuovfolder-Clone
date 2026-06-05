package com.fuoverflow.source.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_question_options")
public class SourceQuestionOptionEntity {
    @Id
    private UUID id;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(name = "option_text", columnDefinition = "text")
    private String optionText;

    @Column(name = "option_image_url", length = 500)
    private String optionImageUrl;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public UUID getQuestionId() { return questionId; }
    public String getOptionText() { return optionText; }
    public String getOptionImageUrl() { return optionImageUrl; }
    public boolean isCorrect() { return correct; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public static SourceQuestionOptionEntity create(
            UUID id,
            UUID questionId,
            String optionText,
            String optionImageUrl,
            boolean correct,
            int sortOrder,
            Instant now) {
        SourceQuestionOptionEntity e = new SourceQuestionOptionEntity();
        e.id = id;
        e.questionId = questionId;
        e.optionText = optionText;
        e.optionImageUrl = optionImageUrl;
        e.correct = correct;
        e.sortOrder = sortOrder;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
