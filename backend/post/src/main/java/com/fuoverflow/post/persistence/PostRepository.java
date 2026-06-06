package com.fuoverflow.post.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {
    @Query("""
            select p from PostEntity p
            where p.threadId = :threadId
              and p.deletedAt is null
              and p.status in :statuses
            """)
    Page<PostEntity> findByThreadIdAndStatuses(
            @Param("threadId") UUID threadId,
            @Param("statuses") List<String> statuses,
            Pageable pageable);

    Optional<PostEntity> findByIdAndThreadIdAndDeletedAtIsNull(UUID id, UUID threadId);

    Optional<PostEntity> findFirstByThreadIdAndDeletedAtIsNullAndStatusInOrderByCreatedAtAsc(
            UUID threadId, List<String> statuses);

    Optional<PostEntity> findFirstByThreadIdAndDeletedAtIsNullAndStatusInOrderByCreatedAtDesc(
            UUID threadId, List<String> statuses);

    long countByThreadIdAndDeletedAtIsNullAndStatusIn(UUID threadId, List<String> statuses);

    @Query("""
            select p from PostEntity p
            where p.deletedAt is null
              and p.status = 'pending'
            order by p.createdAt asc
            """)
    Page<PostEntity> findPendingPosts(Pageable pageable);

    long countByThreadIdAndDeletedAtIsNullAndStatus(UUID threadId, String status);
}
