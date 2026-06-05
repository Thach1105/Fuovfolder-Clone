package com.fuoverflow.thread.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThreadBookmarkRepository extends JpaRepository<ThreadBookmarkEntity, UUID> {
    Optional<ThreadBookmarkEntity> findByThreadIdAndUserId(UUID threadId, UUID userId);

    boolean existsByThreadIdAndUserId(UUID threadId, UUID userId);

    void deleteByThreadIdAndUserId(UUID threadId, UUID userId);

    @Query("""
            SELECT b.threadId FROM ThreadBookmarkEntity b
            WHERE b.userId = :userId AND b.threadId IN :threadIds
            """)
    List<UUID> findWatchedThreadIds(
            @Param("userId") UUID userId,
            @Param("threadIds") Collection<UUID> threadIds);

    @Query("""
            SELECT b.userId FROM ThreadBookmarkEntity b
            WHERE b.threadId = :threadId AND b.userId <> :excludeUserId
            """)
    List<UUID> findWatcherUserIdsExcluding(
            @Param("threadId") UUID threadId,
            @Param("excludeUserId") UUID excludeUserId);

    @Query(
            value = """
                    SELECT t.*
                    FROM threads t
                    INNER JOIN thread_bookmarks b ON b.thread_id = t.id
                    WHERE b.user_id = :userId
                      AND t.deleted_at IS NULL
                      AND t.status NOT IN ('hidden', 'deleted')
                    ORDER BY b.created_at DESC
                    """,
            countQuery = """
                    SELECT COUNT(*)
                    FROM threads t
                    INNER JOIN thread_bookmarks b ON b.thread_id = t.id
                    WHERE b.user_id = :userId
                      AND t.deleted_at IS NULL
                      AND t.status NOT IN ('hidden', 'deleted')
                    """,
            nativeQuery = true)
    Page<ThreadEntity> findWatchedThreads(@Param("userId") UUID userId, Pageable pageable);
}
