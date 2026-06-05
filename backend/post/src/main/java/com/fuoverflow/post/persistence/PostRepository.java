package com.fuoverflow.post.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {
    Page<PostEntity> findByThreadIdAndDeletedAtIsNull(UUID threadId, Pageable pageable);

    long countByThreadIdAndDeletedAtIsNull(UUID threadId);
}
