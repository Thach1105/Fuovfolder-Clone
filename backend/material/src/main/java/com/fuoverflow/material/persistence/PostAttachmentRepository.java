package com.fuoverflow.material.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostAttachmentRepository extends JpaRepository<PostAttachmentEntity, UUID> {
    List<PostAttachmentEntity> findByPostIdOrderBySortOrderAsc(UUID postId);

    List<PostAttachmentEntity> findByPostIdInOrderByPostIdAscSortOrderAsc(Collection<UUID> postIds);

    Optional<PostAttachmentEntity> findByUploadedFileId(UUID uploadedFileId);
}
