package com.fuoverflow.exam.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "exam_pe_resources")
public class ExamPeResourceEntity {
    @Id
    private UUID id;

    @Column(name = "pe_item_id", nullable = false)
    private UUID peItemId;

    @Column(name = "folder_label", length = 255)
    private String folderLabel;

    @Column(name = "object_key", nullable = false, length = 500)
    private String objectKey;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "mime_type", length = 120)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() { return id; }
    public UUID getPeItemId() { return peItemId; }
    public String getFolderLabel() { return folderLabel; }
    public String getObjectKey() { return objectKey; }
    public String getOriginalFilename() { return originalFilename; }
    public String getMimeType() { return mimeType; }
    public long getSizeBytes() { return sizeBytes; }
    public int getSortOrder() { return sortOrder; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDeletedAt() { return deletedAt; }

    public void setFolderLabel(String folderLabel) { this.folderLabel = folderLabel; }
    public void setSortOrder(int sortOrder) { this.sortOrder = sortOrder; }
    public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }

    public static ExamPeResourceEntity create(
            UUID id, UUID peItemId, String folderLabel, String objectKey,
            String originalFilename, String mimeType, long sizeBytes, int sortOrder, Instant now) {
        ExamPeResourceEntity e = new ExamPeResourceEntity();
        e.id = id;
        e.peItemId = peItemId;
        e.folderLabel = folderLabel;
        e.objectKey = objectKey;
        e.originalFilename = originalFilename;
        e.mimeType = mimeType;
        e.sizeBytes = sizeBytes;
        e.sortOrder = sortOrder;
        e.createdAt = now;
        return e;
    }
}
