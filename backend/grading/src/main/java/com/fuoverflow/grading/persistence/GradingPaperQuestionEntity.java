package com.fuoverflow.grading.persistence;

import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperSection;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grading_paper_questions")
public class GradingPaperQuestionEntity {
    @Id
    private UUID id;

    @Column(name = "paper_id", nullable = false)
    private UUID paperId;

    @Column(nullable = false)
    private long qid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private PaperSection section;

    @Column(name = "q_type")
    private Integer qType;

    @Column(name = "display_no", nullable = false)
    private int displayNo;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal mark;

    @Column(name = "chapter_id")
    private Integer chapterId;

    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @Column(name = "image_sha256", length = 64)
    private String imageSha256;

    @Column(name = "content_sha256", nullable = false, length = 64)
    private String contentSha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_mode", nullable = false, length = 12)
    private AnswerMode answerMode;

    @Column(name = "expected_answer_count")
    private Integer expectedAnswerCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "answer_source", length = 12)
    private AnswerSource answerSource;

    @Column(name = "answer_source_ref", length = 120)
    private String answerSourceRef;

    @Column(nullable = false)
    private boolean answered;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected GradingPaperQuestionEntity() {
    }

    public static GradingPaperQuestionEntity of(UUID paperId, long qid, PaperSection section, Integer qType,
                                                int displayNo, BigDecimal mark, Integer chapterId,
                                                String questionText, String imageSha256, String contentSha256,
                                                AnswerMode answerMode, Integer expectedAnswerCount) {
        Instant now = Instant.now();
        GradingPaperQuestionEntity entity = new GradingPaperQuestionEntity();
        entity.id = UUID.randomUUID();
        entity.paperId = paperId;
        entity.qid = qid;
        entity.section = section;
        entity.qType = qType;
        entity.displayNo = displayNo;
        entity.mark = mark;
        entity.chapterId = chapterId;
        entity.questionText = questionText;
        entity.imageSha256 = imageSha256;
        entity.contentSha256 = contentSha256;
        entity.answerMode = answerMode;
        entity.expectedAnswerCount = expectedAnswerCount;
        entity.answered = false;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    /** MANUAL/IMPORTED: a trusted answer key, counts as answered when publishing. */
    public void applyAnswer(AnswerSource source, String sourceRef) {
        if (source == AnswerSource.SUGGESTED) {
            throw new IllegalArgumentException("use suggestAnswer for SUGGESTED");
        }
        this.answerSource = source;
        this.answerSourceRef = sourceRef;
        this.answered = true;
        this.updatedAt = Instant.now();
    }

    /** SUGGESTED: proposed by a source outside our identifier space, never counts as answered. */
    public void suggestAnswer(String sourceRef) {
        this.answerSource = AnswerSource.SUGGESTED;
        this.answerSourceRef = sourceRef;
        this.answered = false;
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPaperId() { return paperId; }
    public long getQid() { return qid; }
    public PaperSection getSection() { return section; }
    public Integer getQType() { return qType; }
    public int getDisplayNo() { return displayNo; }
    public BigDecimal getMark() { return mark; }
    public Integer getChapterId() { return chapterId; }
    public String getQuestionText() { return questionText; }
    public String getImageSha256() { return imageSha256; }
    public String getContentSha256() { return contentSha256; }
    public AnswerMode getAnswerMode() { return answerMode; }
    public Integer getExpectedAnswerCount() { return expectedAnswerCount; }
    public AnswerSource getAnswerSource() { return answerSource; }
    public String getAnswerSourceRef() { return answerSourceRef; }
    public boolean isAnswered() { return answered; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
