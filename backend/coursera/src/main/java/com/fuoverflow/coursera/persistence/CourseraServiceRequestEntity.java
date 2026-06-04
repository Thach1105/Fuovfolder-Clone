package com.fuoverflow.coursera.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coursera_service_requests")
public class CourseraServiceRequestEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(name = "total_points", nullable = false)
    private int totalPoints;

    @Column(name = "pricing_kind", nullable = false, length = 16)
    private String pricingKind;

    @Column(name = "combo_id")
    private UUID comboId;

    @Column(name = "user_notes", columnDefinition = "text")
    private String userNotes;

    @Column(name = "assigned_to_user_id")
    private UUID assignedToUserId;

    @Column(name = "status_changed_at", nullable = false)
    private Instant statusChangedAt;

    @Column(name = "status_changed_by")
    private UUID statusChangedBy;

    @Column(name = "payment_ledger_id")
    private UUID paymentLedgerId;

    @Column(name = "refund_ledger_id")
    private UUID refundLedgerId;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Version
    @Column(name = "lock_version", nullable = false)
    private int lockVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getStatus() { return status; }
    public int getTotalPoints() { return totalPoints; }
    public String getPricingKind() { return pricingKind; }
    public UUID getComboId() { return comboId; }
    public String getUserNotes() { return userNotes; }
    public UUID getAssignedToUserId() { return assignedToUserId; }
    public Instant getStatusChangedAt() { return statusChangedAt; }
    public UUID getStatusChangedBy() { return statusChangedBy; }
    public UUID getPaymentLedgerId() { return paymentLedgerId; }
    public UUID getRefundLedgerId() { return refundLedgerId; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(String status) { this.status = status; }
    public void setStatusChangedAt(Instant statusChangedAt) { this.statusChangedAt = statusChangedAt; }
    public void setStatusChangedBy(UUID statusChangedBy) { this.statusChangedBy = statusChangedBy; }
    public void setAssignedToUserId(UUID assignedToUserId) { this.assignedToUserId = assignedToUserId; }
    public void setPaymentLedgerId(UUID paymentLedgerId) { this.paymentLedgerId = paymentLedgerId; }
    public void setRefundLedgerId(UUID refundLedgerId) { this.refundLedgerId = refundLedgerId; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static CourseraServiceRequestEntity createPending(
            UUID id, UUID userId, int totalPoints, String userNotes, String idempotencyKey, Instant now) {
        CourseraServiceRequestEntity e = new CourseraServiceRequestEntity();
        e.id = id;
        e.userId = userId;
        e.status = "pending";
        e.totalPoints = totalPoints;
        e.pricingKind = "single";
        e.userNotes = userNotes;
        e.idempotencyKey = idempotencyKey;
        e.statusChangedAt = now;
        e.lockVersion = 0;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
