package com.fuoverflow.voucher.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemptionEntity, UUID> {
    long countByVoucherIdAndUserId(UUID voucherId, UUID userId);
    Page<VoucherRedemptionEntity> findByVoucherIdOrderByCreatedAtDesc(UUID voucherId, Pageable pageable);
}
