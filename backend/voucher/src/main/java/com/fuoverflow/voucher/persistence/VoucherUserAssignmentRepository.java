package com.fuoverflow.voucher.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VoucherUserAssignmentRepository extends JpaRepository<VoucherUserAssignmentEntity, UUID> {
    List<VoucherUserAssignmentEntity> findByVoucherId(UUID voucherId);
    boolean existsByVoucherIdAndUserId(UUID voucherId, UUID userId);
    long countByVoucherId(UUID voucherId);
    void deleteByVoucherIdAndUserId(UUID voucherId, UUID userId);
}
