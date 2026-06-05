package com.fuoverflow.thread.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ThreadRepository extends JpaRepository<ThreadEntity, UUID> {
    Optional<ThreadEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ThreadEntity> findBySlugAndDeletedAtIsNull(String slug);

    @Query("""
            select t from ThreadEntity t
            where t.deletedAt is null
              and (:forumId is null or t.forumId = :forumId)
              and (:categoryId is null or t.categoryId = :categoryId)
            """)
    Page<ThreadEntity> browse(
            @Param("forumId") UUID forumId,
            @Param("categoryId") UUID categoryId,
            Pageable pageable);
}
