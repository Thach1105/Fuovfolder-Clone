package com.fuoverflow.source.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "source_catalog_items")
public class SourceCatalogItemEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_points", nullable = false)
    private int pricePoints;

    @Column(name = "access_days", nullable = false)
    private int accessDays;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    @Column(name = "duplication_rate_bp", nullable = false)
    private int duplicationRateBp;

    @Column(name = "pass_rate_bp", nullable = false)
    private int passRateBp;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "card_color", length = 32)
    private String cardColor;

    @Column(name = "cover_image_url", length = 500)
    private String coverImageUrl;

    @Column(name = "category_slug", length = 64)
    private String categorySlug;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "is_featured", nullable = false)
    private boolean featured;

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
    public int getPricePoints() { return pricePoints; }
    public int getAccessDays() { return accessDays; }
    public int getQuestionCount() { return questionCount; }
    public int getDuplicationRateBp() { return duplicationRateBp; }
    public int getPassRateBp() { return passRateBp; }
    public long getViewCount() { return viewCount; }
    public String getCardColor() { return cardColor; }
    public String getCoverImageUrl() { return coverImageUrl; }
    public String getCategorySlug() { return categorySlug; }
    public boolean isActive() { return active; }
    public boolean isFeatured() { return featured; }
    public int getSortOrder() { return sortOrder; }
    public int getLockVersion() { return lockVersion; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setCode(String code) { this.code = code; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setPricePoints(int pricePoints) { this.pricePoints = pricePoints; }
    public void setAccessDays(int accessDays) { this.accessDays = accessDays; }
    public void setQuestionCount(int questionCount) { this.questionCount = questionCount; }
    public void setDuplicationRateBp(int duplicationRateBp) { this.duplicationRateBp = duplicationRateBp; }
    public void setPassRateBp(int passRateBp) { this.passRateBp = passRateBp; }
    public void setCardColor(String cardColor) { this.cardColor = cardColor; }
    public void setCoverImageUrl(String coverImageUrl) { this.coverImageUrl = coverImageUrl; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }
    public void setActive(boolean active) { this.active = active; }
    public void setFeatured(boolean featured) { this.featured = featured; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static SourceCatalogItemEntity create(
            UUID id, String code, String title, String description, int pricePoints,
            int accessDays, int questionCount,             int duplicationRateBp, int passRateBp,
            String cardColor, String coverImageUrl, String categorySlug,
            boolean active, boolean featured, int sortOrder, Instant now) {
        SourceCatalogItemEntity e = new SourceCatalogItemEntity();
        e.id = id;
        e.code = code;
        e.title = title;
        e.description = description;
        e.pricePoints = pricePoints;
        e.accessDays = accessDays;
        e.questionCount = questionCount;
        e.duplicationRateBp = duplicationRateBp;
        e.passRateBp = passRateBp;
        e.viewCount = 0L;
        e.cardColor = cardColor;
        e.coverImageUrl = coverImageUrl;
        e.categorySlug = categorySlug;
        e.active = active;
        e.featured = featured;
        e.sortOrder = sortOrder;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
