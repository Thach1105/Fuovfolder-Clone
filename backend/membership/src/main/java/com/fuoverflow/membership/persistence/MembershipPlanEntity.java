package com.fuoverflow.membership.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "membership_plans")
public class MembershipPlanEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String slug;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @Column(name = "price_cents", nullable = false)
    private int priceCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "billing_interval", nullable = false, length = 32)
    private String billingInterval;

    @Column(nullable = false, length = 32)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "features_json", columnDefinition = "jsonb", nullable = false)
    private String featuresJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getSlug() { return slug; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getPriceCents() { return priceCents; }
    public String getCurrency() { return currency; }
    public String getBillingInterval() { return billingInterval; }
    public String getStatus() { return status; }
    public String getFeaturesJson() { return featuresJson; }

    public void setName(String name) { this.name = name; }
    public void setDescription(String description) { this.description = description; }
    public void setPriceCents(int priceCents) { this.priceCents = priceCents; }
    public void setCurrency(String currency) { this.currency = currency; }
    public void setBillingInterval(String billingInterval) { this.billingInterval = billingInterval; }
    public void setStatus(String status) { this.status = status; }
    public void setFeaturesJson(String featuresJson) { this.featuresJson = featuresJson; }

    public static MembershipPlanEntity create(
            UUID id,
            String slug,
            String name,
            String description,
            int priceCents,
            String currency,
            String billingInterval,
            String status,
            String featuresJson,
            Instant now) {
        MembershipPlanEntity entity = new MembershipPlanEntity();
        entity.id = id;
        entity.slug = slug;
        entity.name = name;
        entity.description = description;
        entity.priceCents = priceCents;
        entity.currency = currency;
        entity.billingInterval = billingInterval;
        entity.status = status;
        entity.featuresJson = featuresJson;
        entity.createdAt = now;
        entity.updatedAt = now;
        return entity;
    }
}
