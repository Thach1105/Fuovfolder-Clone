package com.fuoverflow.payment.persistence;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "point_transactions")
public class PointTransactionEntity {
    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "amount_points", nullable = false)
    private long amountPoints;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "type", nullable = false, length = 64)
    private String type;

    @Column(name = "reference_type", length = 64)
    private String referenceType;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PointTransactionEntity() {
    }

    public static PointTransactionEntity create(UUID userId, long amountPoints, String direction, String type,
                                                 String referenceType, UUID referenceId, UUID paymentId, String description) {
        PointTransactionEntity txn = new PointTransactionEntity();
        txn.id = UUID.randomUUID();
        txn.userId = userId;
        txn.amountPoints = amountPoints;
        txn.direction = direction;
        txn.type = type;
        txn.referenceType = referenceType;
        txn.referenceId = referenceId;
        txn.paymentId = paymentId;
        txn.description = description;
        return txn;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public long getAmountPoints() { return amountPoints; }
    public String getDirection() { return direction; }
    public String getType() { return type; }
    public UUID getReferenceId() { return referenceId; }
    public UUID getPaymentId() { return paymentId; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
