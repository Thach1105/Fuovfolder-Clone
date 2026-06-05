package com.fuoverflow.common.storage;

import com.fuoverflow.common.config.ObjectStorageProperties;
import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class LocalObjectStorage implements ObjectStorage {
    private final ObjectStorageProperties properties;

    public LocalObjectStorage(ObjectStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredObject storeImage(MultipartFile file, String logicalFolder) {
        ObjectStorageSupport.validateImage(file);
        String contentType = file.getContentType();
        String objectKey = ObjectStorageSupport.buildObjectKey(logicalFolder, contentType);
        Path target = resolvePhysicalPath(objectKey);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new BadRequestException("FILE_STORE_FAILED", "Failed to store uploaded file");
        }
        String legacyUrl = ObjectStorageSupport.LEGACY_UPLOADS_PREFIX + objectKey;
        return new StoredObject(objectKey, legacyUrl);
    }

    @Override
    public void delete(String objectKeyOrLegacyReference) {
        String objectKey = normalizeToObjectKey(objectKeyOrLegacyReference);
        if (objectKey == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolvePhysicalPath(objectKey));
        } catch (IOException ignored) {
            // Best-effort cleanup.
        }
    }

    @Override
    public String resolvePublicUrl(String objectKeyOrLegacyReference) {
        String objectKey = normalizeToObjectKey(objectKeyOrLegacyReference);
        if (objectKey == null) {
            return null;
        }
        return ObjectStorageSupport.LEGACY_UPLOADS_PREFIX + objectKey;
    }

    @Override
    public String normalizeToObjectKey(String storedReference) {
        String value = ObjectStorageSupport.blankToNull(storedReference);
        if (value == null) {
            return null;
        }
        if (value.startsWith(ObjectStorageSupport.LEGACY_UPLOADS_PREFIX)) {
            return value.substring(ObjectStorageSupport.LEGACY_UPLOADS_PREFIX.length());
        }
        return value;
    }

    private Path resolvePhysicalPath(String objectKey) {
        String uploadsPath = properties.uploadsPath();
        if (uploadsPath == null || uploadsPath.isBlank()) {
            throw new BadRequestException("STORAGE_NOT_CONFIGURED", "Upload storage path is not configured");
        }
        return Path.of(uploadsPath).resolve(objectKey).normalize();
    }
}
