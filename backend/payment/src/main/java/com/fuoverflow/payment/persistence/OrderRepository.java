package com.fuoverflow.payment.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByProviderAndProviderOrderId(String provider, String providerOrderId);

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderEntity> findByProviderOrderId(String providerOrderId);

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.userId = :userId
              AND o.provider = 'payos'
              AND (:status IS NULL OR o.status = :status)
              AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
              AND (:toDate IS NULL OR o.createdAt <= :toDate)
            ORDER BY o.createdAt DESC
            """)
    Page<OrderEntity> findUserDeposits(
            @Param("userId") UUID userId,
            @Param("status") String status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable);

    @Query("""
            SELECT o FROM OrderEntity o
            WHERE o.provider = 'payos'
              AND (:status IS NULL OR o.status = :status)
              AND (:userId IS NULL OR o.userId = :userId)
              AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
              AND (:toDate IS NULL OR o.createdAt <= :toDate)
            ORDER BY o.createdAt DESC
            """)
    Page<OrderEntity> findAllOrders(
            @Param("status") String status,
            @Param("userId") UUID userId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable);

    @Query("""
            SELECT o.status, COUNT(o), COALESCE(SUM(o.totalCents), 0), COALESCE(SUM(o.pointsAwarded), 0)
            FROM OrderEntity o
            WHERE o.provider = 'payos'
              AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
              AND (:toDate IS NULL OR o.createdAt <= :toDate)
            GROUP BY o.status
            """)
    List<Object[]> aggregateByStatus(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate);

    @Query("""
            SELECT o.tierLabelSnapshot, COUNT(o), COALESCE(SUM(o.totalCents), 0)
            FROM OrderEntity o
            WHERE o.provider = 'payos' AND o.status = 'paid'
              AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
              AND (:toDate IS NULL OR o.createdAt <= :toDate)
            GROUP BY o.tierLabelSnapshot
            ORDER BY SUM(o.totalCents) DESC
            """)
    List<Object[]> revenueByTier(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate);

    @Query("""
            SELECT o.userId, COUNT(o), COALESCE(SUM(o.totalCents), 0)
            FROM OrderEntity o
            WHERE o.provider = 'payos' AND o.status = 'paid'
              AND (:fromDate IS NULL OR o.createdAt >= :fromDate)
              AND (:toDate IS NULL OR o.createdAt <= :toDate)
            GROUP BY o.userId
            ORDER BY SUM(o.totalCents) DESC
            """)
    List<Object[]> topUsersByRevenue(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate, Pageable pageable);
}
