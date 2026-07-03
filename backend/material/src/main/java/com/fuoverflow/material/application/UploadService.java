package com.fuoverflow.material.application;

import com.fuoverflow.common.config.UploadProperties;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.security.UploadPermissionChecker;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.common.storage.StoredObject;
import com.fuoverflow.material.api.dto.UploadResponse;
import com.fuoverflow.material.domain.UploadPurpose;
import com.fuoverflow.material.persistence.UploadedFileEntity;
import com.fuoverflow.material.persistence.UploadedFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class UploadService {
    private final UploadedFileRepository uploadedFileRepository;
    private final ObjectStorage objectStorage;
    private final UploadRateLimiter rateLimiter;
    private final UploadProperties uploadProperties;
    private final UploadPermissionChecker uploadPermissionChecker;

    public UploadService(
            UploadedFileRepository uploadedFileRepository,
            ObjectStorage objectStorage,
            UploadRateLimiter rateLimiter,
            UploadProperties uploadProperties,
            UploadPermissionChecker uploadPermissionChecker) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.objectStorage = objectStorage;
        this.rateLimiter = rateLimiter;
        this.uploadProperties = uploadProperties;
        this.uploadPermissionChecker = uploadPermissionChecker;
    }

    @Transactional
    public UploadResponse upload(MultipartFile file, UploadPurpose purpose, UUID ownerUserId) {
        requirePurposePermission(ownerUserId, purpose);
        rateLimiter.checkAllowed(ownerUserId, uploadProperties.maxUploadsPerHour());

        StoredObject stored = objectStorage.storeFile(file, purpose.folder(), purpose.fileKind());
        Instant now = Instant.now();
        UUID fileId = UUID.randomUUID();
        String mimeType = normalizeMime(file.getContentType());
        String filename = com.fuoverflow.common.storage.FileContentValidator.sanitizeFilename(file.getOriginalFilename());
        UploadedFileEntity entity = UploadedFileEntity.create(
                fileId,
                ownerUserId,
                filename,
                stored.objectKey(),
                stored.publicUrl(),
                fileCategoryFor(purpose),
                mimeType,
                file.getSize(),
                purpose.publicReadable() ? "public" : "private",
                purpose.slug(),
                mimeType,
                now);
        if (autoLinkOnUpload(purpose)) {
            entity.setLinkedAt(now);
        }
        uploadedFileRepository.save(entity);
        return new UploadResponse(
                fileId,
                stored.objectKey(),
                stored.publicUrl(),
                mimeType,
                file.getSize(),
                filename);
    }

    @Transactional(readOnly = true)
    public UploadedFileEntity requireOwnedStagingFile(UUID fileId, UUID ownerUserId, UploadPurpose purpose) {
        UploadedFileEntity file = uploadedFileRepository.findByIdAndOwnerUserIdAndDeletedAtIsNull(fileId, ownerUserId)
                .orElseThrow(() -> new BadRequestException("FILE_NOT_FOUND", "Uploaded file not found"));
        if (!purpose.slug().equals(file.getPurpose())) {
            throw new BadRequestException("FILE_PURPOSE_MISMATCH", "Uploaded file purpose does not match");
        }
        if (file.getLinkedAt() != null) {
            throw new BadRequestException("FILE_ALREADY_LINKED", "Uploaded file is already linked");
        }
        if (!"active".equals(file.getStatus())) {
            throw new BadRequestException("FILE_NOT_ACTIVE", "Uploaded file is not active");
        }
        return file;
    }

    @Transactional
    public void markLinked(UUID fileId, UUID ownerUserId, UploadPurpose purpose) {
        UploadedFileEntity file = requireOwnedStagingFile(fileId, ownerUserId, purpose);
        file.setLinkedAt(Instant.now());
        uploadedFileRepository.save(file);
    }

    @Transactional
    public void markLinkedByObjectKey(String objectKey, UUID ownerUserId, UploadPurpose purpose) {
        uploadedFileRepository.findByStoragePathAndOwnerUserIdAndDeletedAtIsNull(objectKey, ownerUserId)
                .filter(file -> purpose.slug().equals(file.getPurpose()))
                .ifPresent(file -> {
                    if (file.getLinkedAt() == null) {
                        file.setLinkedAt(Instant.now());
                        uploadedFileRepository.save(file);
                    }
                });
    }

    @Transactional(readOnly = true)
    public UploadedFileEntity requireActiveFile(UUID fileId) {
        return uploadedFileRepository.findById(fileId)
                .filter(file -> file.getDeletedAt() == null && "active".equals(file.getStatus()))
                .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "File not found"));
    }

    @Transactional
    public void deleteStoredFile(UploadedFileEntity file) {
        objectStorage.delete(file.getStoragePath());
        file.setStatus("deleted");
        file.setDeletedAt(Instant.now());
        uploadedFileRepository.save(file);
    }

    public String resolvePublicUrl(String storedReference) {
        if (storedReference == null || storedReference.isBlank()) {
            return null;
        }
        return objectStorage.resolvePublicUrl(storedReference);
    }

    @Transactional
    public void deleteByStorageReference(String objectKeyOrLegacyReference) {
        if (objectKeyOrLegacyReference == null || objectKeyOrLegacyReference.isBlank()) {
            return;
        }
        String objectKey = objectStorage.normalizeToObjectKey(objectKeyOrLegacyReference);
        uploadedFileRepository.findByStoragePathAndDeletedAtIsNull(objectKey)
                .ifPresent(this::deleteStoredFile);
        objectStorage.delete(objectKeyOrLegacyReference);
    }

    @Transactional
    public void markLinkedByStoragePath(String objectKeyOrReference) {
        if (objectKeyOrReference == null || objectKeyOrReference.isBlank()) {
            return;
        }
        String normalized = objectStorage.normalizeToObjectKey(objectKeyOrReference);
        uploadedFileRepository.findByStoragePathAndDeletedAtIsNull(normalized)
                .ifPresent(file -> {
                    if (file.getLinkedAt() == null) {
                        file.setLinkedAt(Instant.now());
                        uploadedFileRepository.save(file);
                    }
                });
    }

    @Transactional
    public void markUnlinkedByObjectKey(String objectKeyOrReference) {
        if (objectKeyOrReference == null || objectKeyOrReference.isBlank()) {
            return;
        }
        String normalized = objectStorage.normalizeToObjectKey(objectKeyOrReference);
        uploadedFileRepository.findByStoragePathAndDeletedAtIsNull(normalized)
                .ifPresent(file -> {
                    file.setLinkedAt(null);
                    uploadedFileRepository.save(file);
                });
    }

    @Transactional(readOnly = true)
    public Optional<UploadedFileEntity> findByStoragePath(String objectKey) {
        return uploadedFileRepository.findByStoragePathAndDeletedAtIsNull(objectKey);
    }

    private void requirePurposePermission(UUID userId, UploadPurpose purpose) {
        if (!uploadPermissionChecker.hasAnyPermission(userId, purpose.requiredPermissions())) {
            throw new ForbiddenException("UPLOAD_FORBIDDEN", "You cannot upload files for this purpose");
        }
    }

    private static String fileCategoryFor(UploadPurpose purpose) {
        return switch (purpose) {
            case AVATAR -> "avatar";
            case FORUM_ATTACHMENT -> "attachment";
            default -> "image";
        };
    }

    private static boolean autoLinkOnUpload(UploadPurpose purpose) {
        return switch (purpose) {
            case FORUM_IMAGE -> true;
            default -> false;
        };
    }

    private static String normalizeMime(String contentType) {
        if (contentType == null) {
            return "application/octet-stream";
        }
        return contentType.toLowerCase().split(";")[0].trim();
    }
}
