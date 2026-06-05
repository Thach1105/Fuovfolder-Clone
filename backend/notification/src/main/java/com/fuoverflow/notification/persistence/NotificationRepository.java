package com.fuoverflow.notification.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<NotificationEntity, UUID> {
    Page<NotificationEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
            SELECT n FROM NotificationEntity n
            WHERE n.userId = :userId AND n.readAt IS NULL
            ORDER BY n.createdAt DESC
            """)
    Page<NotificationEntity> findUnreadByUserId(@Param("userId") UUID userId, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<NotificationEntity> findByIdAndUserId(UUID id, UUID userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE NotificationEntity n
            SET n.readAt = :readAt
            WHERE n.userId = :userId AND n.readAt IS NULL
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("readAt") Instant readAt);
}
