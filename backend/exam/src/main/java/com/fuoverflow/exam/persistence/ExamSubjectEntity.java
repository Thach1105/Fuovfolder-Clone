package com.fuoverflow.exam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_subjects")
public class ExamSubjectEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "card_color", length = 32)
    private String cardColor;

    @Column(name = "category_slug", length = 64)
    private String categorySlug;

    @Column(name = "curriculum_term")
    private Integer curriculumTerm;

    @Column(name = "fe_preview_image_count", nullable = false)
    private int fePreviewImageCount;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "is_active", nullable = false)
    private boolean active;

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
    public String getCode() { return code; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getCardColor() { return cardColor; }
    public String getCategorySlug() { return categorySlug; }
    public Integer getCurriculumTerm() { return curriculumTerm; }
    public int getFePreviewImageCount() { return fePreviewImageCount; }
    public long getViewCount() { return viewCount; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public int getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setCode(String code) { this.code = code; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public void setCardColor(String cardColor) { this.cardColor = cardColor; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }
    public void setCurriculumTerm(Integer curriculumTerm) { this.curriculumTerm = curriculumTerm; }
    public void setFePreviewImageCount(int fePreviewImageCount) { this.fePreviewImageCount = fePreviewImageCount; }
    public void setActive(boolean active) { this.active = active; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static ExamSubjectEntity create(
            UUID id, String code, String title, String description,
            String coverImageUrl, String cardColor, String categorySlug, Integer curriculumTerm,
            int fePreviewImageCount, boolean active, int sortOrder, Instant now) {
        ExamSubjectEntity e = new ExamSubjectEntity();
        e.id = id;
        e.code = code;
        e.title = title;
        e.description = description;
        e.coverImageUrl = coverImageUrl;
        e.cardColor = cardColor;
        e.categorySlug = categorySlug;
        e.curriculumTerm = curriculumTerm;
        e.fePreviewImageCount = fePreviewImageCount;
        e.viewCount = 0L;
        e.active = active;
        e.sortOrder = sortOrder;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
