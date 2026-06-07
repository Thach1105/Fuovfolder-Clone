package com.fuoverflow.material.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uploaded_files")
public class UploadedFileEntity {
    @Id
    private UUID id;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "storage_path", nullable = false, columnDefinition = "text")
    private String storagePath;

    @Column(name = "public_url", columnDefinition = "text")
    private String publicUrl;

    @Column(name = "file_category", nullable = false, length = 32)
    private String fileCategory;

    @Column(name = "mime_type", nullable = false, length = 120)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "checksum_sha256", length = 64)
    private String checksumSha256;

    @Column(nullable = false, length = 32)
    private String visibility;

    @Column(nullable = false, length = 32)
    private String status;

    @Column(nullable = false, length = 64)
    private String purpose;

    @Column(name = "original_mime", length = 120)
    private String originalMime;

    @Column(name = "linked_at")
    private Instant linkedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    public UUID getId() {
        return id;
    }

    public UUID getOwnerUserId() {
        return ownerUserId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public String getPublicUrl() {
        return publicUrl;
    }

    public String getFileCategory() {
        return fileCategory;
    }

    public String getMimeType() {
        return mimeType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getVisibility() {
        return visibility;
    }

    public String getStatus() {
        return status;
    }

    public String getPurpose() {
        return purpose;
    }

    public Instant getLinkedAt() {
        return linkedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getDeletedAt() {
        return deletedAt;
    }

    public void setLinkedAt(Instant linkedAt) {
        this.linkedAt = linkedAt;
    }

    public void setDeletedAt(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public static UploadedFileEntity create(
            UUID id,
            UUID ownerUserId,
            String originalFilename,
            String storagePath,
            String publicUrl,
            String fileCategory,
            String mimeType,
            long sizeBytes,
            String visibility,
            String purpose,
            String originalMime,
            Instant now) {
        UploadedFileEntity entity = new UploadedFileEntity();
        entity.id = id;
        entity.ownerUserId = ownerUserId;
        entity.originalFilename = originalFilename;
        entity.storagePath = storagePath;
        entity.publicUrl = publicUrl;
        entity.fileCategory = fileCategory;
        entity.mimeType = mimeType;
        entity.sizeBytes = sizeBytes;
        entity.visibility = visibility;
        entity.status = "active";
        entity.purpose = purpose;
        entity.originalMime = originalMime;
        entity.createdAt = now;
        return entity;
    }
}
