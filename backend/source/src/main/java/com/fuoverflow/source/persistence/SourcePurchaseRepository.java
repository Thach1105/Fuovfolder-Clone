package com.fuoverflow.source.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourcePurchaseRepository
        extends JpaRepository<SourcePurchaseEntity, UUID>, JpaSpecificationExecutor<SourcePurchaseEntity> {
    Optional<SourcePurchaseEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<SourcePurchaseEntity> findByIdAndUserId(UUID id, UUID userId);

    Optional<SourcePurchaseEntity> findFirstByUserIdAndCatalogItemIdAndStatusOrderByEndsAtDesc(
            UUID userId, UUID catalogItemId, String status);

    Page<SourcePurchaseEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
            select p from SourcePurchaseEntity p
            where p.userId = :userId and p.status = 'active' and p.endsAt > :now
            order by p.endsAt desc
            """)
    Page<SourcePurchaseEntity> findActiveForUser(
            @Param("userId") UUID userId, @Param("now") Instant now, Pageable pageable);

    @Query("""
            select p from SourcePurchaseEntity p
            where p.userId = :userId
              and (p.status = 'expired' or (p.status = 'active' and p.endsAt <= :now))
            order by p.endsAt desc
            """)
    Page<SourcePurchaseEntity> findExpiredForUser(
            @Param("userId") UUID userId, @Param("now") Instant now, Pageable pageable);

    long countByUserId(UUID userId);

    @Query("""
            select count(p) from SourcePurchaseEntity p
            where p.userId = :userId and p.status = 'active' and p.endsAt > :now
            """)
    long countActiveForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            select count(p) from SourcePurchaseEntity p
            where p.userId = :userId
              and (p.status = 'expired' or (p.status = 'active' and p.endsAt <= :now))
            """)
    long countExpiredForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    @Query("""
            select count(p) from SourcePurchaseEntity p
            where p.userId = :userId and p.status = 'refunded'
            """)
    long countRefundedForUser(@Param("userId") UUID userId);

    boolean existsByUserIdAndCatalogItemIdAndStatusAndEndsAtAfter(
            UUID userId, UUID catalogItemId, String status, Instant now);

    Optional<SourcePurchaseEntity> findFirstByUserIdAndCatalogItemIdAndStatusAndEndsAtAfterOrderByEndsAtDesc(
            UUID userId, UUID catalogItemId, String status, Instant now);

    @Query("""
            select coalesce(sum(p.unitPricePoints), 0) from SourcePurchaseEntity p
            where p.paymentLedgerId is not null
            """)
    long sumPaidPoints();

    @Query("""
            select coalesce(sum(p.unitPricePoints), 0) from SourcePurchaseEntity p
            where p.refundLedgerId is not null
            """)
    long sumRefundedPoints();

    @Query("select p.status, count(p) from SourcePurchaseEntity p group by p.status")
    List<Object[]> countByStatusAll();

    @Modifying
    @Query("""
            update SourcePurchaseEntity p
            set p.status = 'expired', p.updatedAt = :now
            where p.status = 'active' and p.endsAt <= :now
            """)
    int markExpired(@Param("now") Instant now);
}
