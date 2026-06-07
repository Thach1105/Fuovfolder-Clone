package com.fuoverflow.material.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "post_attachments")
public class PostAttachmentEntity {
    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "uploaded_file_id", nullable = false)
    private UUID uploadedFileId;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public UUID getPostId() {
        return postId;
    }

    public UUID getUploadedFileId() {
        return uploadedFileId;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public static PostAttachmentEntity create(UUID id, UUID postId, UUID uploadedFileId, int sortOrder, Instant now) {
        PostAttachmentEntity entity = new PostAttachmentEntity();
        entity.id = id;
        entity.postId = postId;
        entity.uploadedFileId = uploadedFileId;
        entity.sortOrder = sortOrder;
        entity.createdAt = now;
        return entity;
    }
}
