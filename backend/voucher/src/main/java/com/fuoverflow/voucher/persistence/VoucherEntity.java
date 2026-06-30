package com.fuoverflow.voucher.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vouchers")
public class VoucherEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "discount_type", nullable = false, length = 16)
    private String discountType;

    @Column(name = "discount_value", nullable = false)
    private int discountValue;

    @Column(name = "max_discount_points")
    private Integer maxDiscountPoints;

    @Column(name = "min_order_points", nullable = false)
    private int minOrderPoints;

    @Column(name = "max_usage", nullable = false)
    private int maxUsage;

    @Column(name = "used_count", nullable = false)
    private int usedCount;

    @Column(name = "max_usage_per_user", nullable = false)
    private int maxUsagePerUser;

    @Column(name = "applicable_types", nullable = false)
    private String applicableTypes;

    @Column(name = "required_membership_slugs")
    private String requiredMembershipSlugs;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getCode() { return code; }
    public String getDescription() { return description; }
    public String getDiscountType() { return discountType; }
    public int getDiscountValue() { return discountValue; }
    public Integer getMaxDiscountPoints() { return maxDiscountPoints; }
    public int getMinOrderPoints() { return minOrderPoints; }
    public int getMaxUsage() { return maxUsage; }
    public int getUsedCount() { return usedCount; }
    public int getMaxUsagePerUser() { return maxUsagePerUser; }
    public String getApplicableTypes() { return applicableTypes; }
    public String getRequiredMembershipSlugs() { return requiredMembershipSlugs; }
    public Instant getStartsAt() { return startsAt; }
    public Instant getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }
    public UUID getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setCode(String code) { this.code = code; }
    public void setDescription(String description) { this.description = description; }
    public void setDiscountType(String discountType) { this.discountType = discountType; }
    public void setDiscountValue(int discountValue) { this.discountValue = discountValue; }
    public void setMaxDiscountPoints(Integer maxDiscountPoints) { this.maxDiscountPoints = maxDiscountPoints; }
    public void setMinOrderPoints(int minOrderPoints) { this.minOrderPoints = minOrderPoints; }
    public void setMaxUsage(int maxUsage) { this.maxUsage = maxUsage; }
    public void setMaxUsagePerUser(int maxUsagePerUser) { this.maxUsagePerUser = maxUsagePerUser; }
    public void setApplicableTypes(String applicableTypes) { this.applicableTypes = applicableTypes; }
    public void setRequiredMembershipSlugs(String requiredMembershipSlugs) { this.requiredMembershipSlugs = requiredMembershipSlugs; }
    public void setStartsAt(Instant startsAt) { this.startsAt = startsAt; }
    public void setEndsAt(Instant endsAt) { this.endsAt = endsAt; }
    public void setActive(boolean active) { this.active = active; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static VoucherEntity create(
            UUID id, String code, String description, String discountType, int discountValue,
            Integer maxDiscountPoints, int minOrderPoints, int maxUsage, int maxUsagePerUser,
            String applicableTypes, String requiredMembershipSlugs,
            Instant startsAt, Instant endsAt, UUID createdBy, Instant now) {
        VoucherEntity e = new VoucherEntity();
        e.id = id;
        e.code = code.toUpperCase();
        e.description = description;
        e.discountType = discountType;
        e.discountValue = discountValue;
        e.maxDiscountPoints = maxDiscountPoints;
        e.minOrderPoints = minOrderPoints;
        e.maxUsage = maxUsage;
        e.usedCount = 0;
        e.maxUsagePerUser = maxUsagePerUser;
        e.applicableTypes = applicableTypes;
        e.requiredMembershipSlugs = requiredMembershipSlugs;
        e.startsAt = startsAt;
        e.endsAt = endsAt;
        e.active = true;
        e.createdBy = createdBy;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
