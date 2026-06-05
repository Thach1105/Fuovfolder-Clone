package com.fuoverflow.thread.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ThreadRepository extends JpaRepository<ThreadEntity, UUID> {
    Optional<ThreadEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ThreadEntity> findBySlugAndDeletedAtIsNull(String slug);

    @Query("""
            select t from ThreadEntity t
            where t.deletedAt is null
              and t.status not in ('hidden', 'deleted')
              and (:forumId is null or t.forumId = :forumId)
              and (:categoryId is null or t.categoryId = :categoryId)
            """)
    Page<ThreadEntity> browse(
            @Param("forumId") UUID forumId,
            @Param("categoryId") UUID categoryId,
            Pageable pageable);

    @Query(
            value = """
                    SELECT category_id AS categoryId,
                           COUNT(*) AS topicCount,
                           COALESCE(SUM(reply_count + 1), 0) AS postCount
                    FROM threads
                    WHERE deleted_at IS NULL
                      AND forum_id = :forumId
                      AND status NOT IN ('hidden', 'deleted')
                    GROUP BY category_id
                    """,
            nativeQuery = true)
    List<CategoryThreadStatsProjection> aggregateStatsByForum(@Param("forumId") UUID forumId);

    @Query(
            value = """
                    SELECT DISTINCT ON (category_id)
                           category_id AS categoryId,
                           id AS threadId,
                           title AS title,
                           thread_type AS threadType,
                           imported_author_handle AS authorHandle,
                           last_post_at AS lastPostAt
                    FROM threads
                    WHERE deleted_at IS NULL
                      AND forum_id = :forumId
                      AND status NOT IN ('hidden', 'deleted')
                    ORDER BY category_id, last_post_at DESC
                    """,
            nativeQuery = true)
    List<CategoryLatestThreadProjection> latestThreadByCategoryForForum(@Param("forumId") UUID forumId);
}
