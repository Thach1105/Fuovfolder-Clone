package com.fuoverflow.post.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {
    @org.springframework.data.jpa.repository.Query("""
            select p from PostEntity p
            where p.threadId = :threadId
              and p.deletedAt is null
              and p.status = 'visible'
            """)
    Page<PostEntity> findByThreadIdAndDeletedAtIsNull(
            @org.springframework.data.repository.query.Param("threadId") UUID threadId,
            Pageable pageable);

    long countByThreadIdAndDeletedAtIsNullAndStatus(UUID threadId, String status);
}
