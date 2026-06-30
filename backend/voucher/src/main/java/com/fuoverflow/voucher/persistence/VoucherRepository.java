package com.fuoverflow.voucher.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VoucherRepository extends JpaRepository<VoucherEntity, UUID> {

    @Query("SELECT v FROM VoucherEntity v WHERE upper(v.code) = upper(:code) AND v.active = true")
    Optional<VoucherEntity> findActiveByCode(@Param("code") String code);

    Optional<VoucherEntity> findByCodeIgnoreCase(String code);

    @Modifying
    @Query("""
            UPDATE VoucherEntity v SET v.usedCount = v.usedCount + 1, v.updatedAt = CURRENT_TIMESTAMP
            WHERE v.id = :id AND v.usedCount < v.maxUsage AND v.active = true
            """)
    int atomicIncrementUsedCount(@Param("id") UUID id);
}
