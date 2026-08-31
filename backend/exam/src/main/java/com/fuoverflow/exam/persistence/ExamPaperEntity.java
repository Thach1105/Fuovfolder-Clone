package com.fuoverflow.exam.persistence;

import com.fuoverflow.exam.domain.ExamPaperStatus;
import com.fuoverflow.exam.domain.ExamPaperType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One exam paper: an FE question set or a PE practical paper. Groups the FE questions / PE items
 * that used to hang flat off a subject, and carries the delivery metadata (exam code, term,
 * content fingerprint) that a webhook ingest needs in order to be idempotent.
 */
@Entity
@Table(name = "exam_papers")
public class ExamPaperEntity {
    @Id
    private UUID id;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "paper_type", nullable = false, length = 8)
    private String paperType;

    @Column(name = "exam_code", nullable = false, length = 120)
    private String examCode;

    @Column(name = "term", length = 16)
    private String term;

    @Column(name = "retake_label", length = 64)
    private String retakeLabel;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    @Column(name = "total_mark", precision = 6, scale = 2)
    private BigDecimal totalMark;

    @Column(name = "declared_question_count")
    private Integer declaredQuestionCount;

    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "ingest_source", length = 64)
    private String ingestSource;

    @Column(name = "external_paper_id", length = 64)
    private String externalPaperId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getSubjectId() { return subjectId; }
    public String getPaperType() { return paperType; }
    public String getExamCode() { return examCode; }
    public String getTerm() { return term; }
    public String getRetakeLabel() { return retakeLabel; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public BigDecimal getTotalMark() { return totalMark; }
    public Integer getDeclaredQuestionCount() { return declaredQuestionCount; }
    public String getFingerprint() { return fingerprint; }
    public String getStatus() { return status; }
    public String getIngestSource() { return ingestSource; }
    public String getExternalPaperId() { return externalPaperId; }
    public int getSortOrder() { return sortOrder; }
    public long getViewCount() { return viewCount; }
    public int getLockVersion() { return lockVersion; }
    public Instant getPublishedAt() { return publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setTitle(String title) { this.title = title; }
    public void setTerm(String term) { this.term = term; }
    public void setRetakeLabel(String retakeLabel) { this.retakeLabel = retakeLabel; }
    public void setDescription(String description) { this.description = description; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public void setTotalMark(BigDecimal totalMark) { this.totalMark = totalMark; }
    public void setDeclaredQuestionCount(Integer count) { this.declaredQuestionCount = count; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setExternalPaperId(String externalPaperId) { this.externalPaperId = externalPaperId; }
    public void setIngestSource(String ingestSource) { this.ingestSource = ingestSource; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public ExamPaperType paperTypeEnum() {
        return ExamPaperType.valueOf(paperType);
    }

    public boolean isPublished() {
        return ExamPaperStatus.PUBLISHED.dbValue().equals(status);
    }

    public void publish(Instant at) {
        this.status = ExamPaperStatus.PUBLISHED.dbValue();
        this.publishedAt = at;
        this.updatedAt = at;
    }

    public static ExamPaperEntity draft(
            UUID id, UUID subjectId, ExamPaperType paperType, String examCode, String term,
            String retakeLabel, String title, String description, Integer durationMinutes,
            BigDecimal totalMark, Integer declaredQuestionCount, String fingerprint,
            String ingestSource, String externalPaperId, int sortOrder, Instant now) {
        ExamPaperEntity e = new ExamPaperEntity();
        e.id = id;
        e.subjectId = subjectId;
        e.paperType = paperType.dbValue();
        e.examCode = examCode;
        e.term = term;
        e.retakeLabel = retakeLabel;
        e.title = title;
        e.description = description;
        e.durationMinutes = durationMinutes;
        e.totalMark = totalMark;
        e.declaredQuestionCount = declaredQuestionCount;
        e.fingerprint = fingerprint;
        e.status = ExamPaperStatus.DRAFT.dbValue();
        e.ingestSource = ingestSource;
        e.externalPaperId = externalPaperId;
        e.sortOrder = sortOrder;
        e.viewCount = 0;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
