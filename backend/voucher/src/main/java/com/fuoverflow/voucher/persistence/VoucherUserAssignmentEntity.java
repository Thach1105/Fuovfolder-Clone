package com.fuoverflow.voucher.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "voucher_user_assignments")
public class VoucherUserAssignmentEntity {
    @Id
    private UUID id;

    @Column(name = "voucher_id", nullable = false)
    private UUID voucherId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() { return id; }
    public UUID getVoucherId() { return voucherId; }
    public UUID getUserId() { return userId; }
    public Instant getCreatedAt() { return createdAt; }

    public static VoucherUserAssignmentEntity create(UUID id, UUID voucherId, UUID userId, Instant now) {
        VoucherUserAssignmentEntity e = new VoucherUserAssignmentEntity();
        e.id = id;
        e.voucherId = voucherId;
        e.userId = userId;
        e.createdAt = now;
        return e;
    }
}
