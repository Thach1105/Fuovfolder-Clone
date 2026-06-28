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

    @Query(value = """
            SELECT * FROM orders
            WHERE user_id = :userId
              AND provider = 'payos'
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
              AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR created_at <= :toDate)
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM orders
            WHERE user_id = :userId
              AND provider = 'payos'
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
              AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR created_at <= :toDate)
            """,
            nativeQuery = true)
    Page<OrderEntity> findUserDeposits(
            @Param("userId") UUID userId,
            @Param("status") String status,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable);

    @Query(value = """
            SELECT * FROM orders
            WHERE provider = 'payos'
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
              AND (CAST(:userId AS uuid) IS NULL OR user_id = :userId)
              AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR created_at <= :toDate)
            ORDER BY created_at DESC
            """,
            countQuery = """
            SELECT count(*) FROM orders
            WHERE provider = 'payos'
              AND (CAST(:status AS varchar) IS NULL OR status = :status)
              AND (CAST(:userId AS uuid) IS NULL OR user_id = :userId)
              AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR created_at <= :toDate)
            """,
            nativeQuery = true)
    Page<OrderEntity> findAllOrders(
            @Param("status") String status,
            @Param("userId") UUID userId,
            @Param("fromDate") Instant fromDate,
            @Param("toDate") Instant toDate,
            Pageable pageable);

    @Query(value = """
            SELECT status, COUNT(*), COALESCE(SUM(total_cents), 0), COALESCE(SUM(points_awarded), 0)
            FROM orders
            WHERE provider = 'payos'
              AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR created_at <= :toDate)
            GROUP BY status
            """, nativeQuery = true)
    List<Object[]> aggregateByStatus(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate);

    @Query(value = """
            SELECT tier_label_snapshot, COUNT(*), COALESCE(SUM(total_cents), 0)
            FROM orders
            WHERE provider = 'payos' AND status = 'paid'
              AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR created_at <= :toDate)
            GROUP BY tier_label_snapshot
            ORDER BY SUM(total_cents) DESC
            """, nativeQuery = true)
    List<Object[]> revenueByTier(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate);

    @Query(value = """
            SELECT user_id, COUNT(*), COALESCE(SUM(total_cents), 0)
            FROM orders
            WHERE provider = 'payos' AND status = 'paid'
              AND (CAST(:fromDate AS timestamptz) IS NULL OR created_at >= :fromDate)
              AND (CAST(:toDate AS timestamptz) IS NULL OR created_at <= :toDate)
            GROUP BY user_id
            ORDER BY SUM(total_cents) DESC
            """, nativeQuery = true)
    List<Object[]> topUsersByRevenue(@Param("fromDate") Instant fromDate, @Param("toDate") Instant toDate, Pageable pageable);
}
