package com.fuoverflow.grading.persistence;

import com.fuoverflow.grading.domain.PaperStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grading_papers")
public class GradingPaperEntity {
    @Id
    private UUID id;

    @Column(name = "exam_code", nullable = false, length = 120)
    private String examCode;

    @Column(name = "subject_code", nullable = false, length = 40)
    private String subjectCode;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "total_mark", precision = 6, scale = 2)
    private BigDecimal totalMark;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PaperStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    private String rawPayload;

    @Column(name = "payload_sha256", nullable = false, length = 64)
    private String payloadSha256;

    @Column(name = "source_payload_id")
    private UUID sourcePayloadId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected GradingPaperEntity() {
    }

    public static GradingPaperEntity draft(String examCode, String subjectCode, String fingerprint,
                                           int questionCount, Integer durationMinutes, BigDecimal totalMark,
                                           String rawPayload, String payloadSha256, UUID sourcePayloadId,
                                           UUID createdBy) {
        Instant now = Instant.now();
        GradingPaperEntity entity = new GradingPaperEntity();
        entity.id = UUID.randomUUID();
        entity.examCode = examCode;
        entity.subjectCode = subjectCode;
        entity.fingerprint = fingerprint;
        entity.questionCount = questionCount;
        entity.durationMinutes = durationMinutes;
        entity.totalMark = totalMark;
        entity.status = PaperStatus.DRAFT;
        entity.rawPayload = rawPayload;
        entity.payloadSha256 = payloadSha256;
        entity.sourcePayloadId = sourcePayloadId;
        entity.createdBy = createdBy;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }

    public void markReady() {
        this.status = PaperStatus.READY;
        this.publishedAt = Instant.now();
        this.updatedAt = this.publishedAt;
    }

    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = this.deletedAt;
    }

    public UUID getId() { return id; }
    public String getExamCode() { return examCode; }
    public String getSubjectCode() { return subjectCode; }
    public String getFingerprint() { return fingerprint; }
    public int getQuestionCount() { return questionCount; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public BigDecimal getTotalMark() { return totalMark; }
    public PaperStatus getStatus() { return status; }
    public String getRawPayload() { return rawPayload; }
    public String getPayloadSha256() { return payloadSha256; }
    public UUID getSourcePayloadId() { return sourcePayloadId; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
}
