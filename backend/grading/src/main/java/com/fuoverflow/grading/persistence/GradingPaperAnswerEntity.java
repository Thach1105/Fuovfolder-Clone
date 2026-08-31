package com.fuoverflow.grading.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grading_paper_answers")
public class GradingPaperAnswerEntity {
    @Id
    private UUID id;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    @Column(name = "question_id", nullable = false)
    private UUID questionId;

    @Column(nullable = false)
    private long qid;

    @Column(nullable = false)
    private long qaid;

    @Column(name = "option_index", nullable = false)
    private int optionIndex;

    @Column(name = "option_text", columnDefinition = "text")
    private String optionText;

    @Column(name = "option_sha256", length = 64)
    private String optionSha256;

    @Column(name = "is_correct", nullable = false)
    private boolean correct;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GradingPaperAnswerEntity() {
    }

    public static GradingPaperAnswerEntity of(UUID paperId, UUID questionId, long qid, long qaid,
                                              int optionIndex, String optionText, String optionSha256) {
        Instant now = Instant.now();
        GradingPaperAnswerEntity entity = new GradingPaperAnswerEntity();
        entity.id = UUID.randomUUID();
        entity.paperId = paperId;
        entity.questionId = questionId;
        entity.qid = qid;
        entity.qaid = qaid;
        entity.optionIndex = optionIndex;
        entity.optionText = optionText;
        entity.optionSha256 = optionSha256;
        entity.correct = false;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void setCorrect(boolean value) {
        this.correct = value;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaperId() { return paperId; }
    public UUID getQuestionId() { return questionId; }
    public long getQid() { return qid; }
    public long getQaid() { return qaid; }
    public int getOptionIndex() { return optionIndex; }
    public String getOptionText() { return optionText; }
    public String getOptionSha256() { return optionSha256; }
    public boolean isCorrect() { return correct; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
