package com.fuoverflow.common.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/**
 * Abstraction over object/blob storage backends (local disk, MinIO, AWS S3, etc.).
 */
public interface ObjectStorage {

    StoredObject storeImage(MultipartFile file, String logicalFolder);

    StoredObject storeFile(MultipartFile file, String logicalFolder, FileKind kind);

    InputStream openStream(String objectKeyOrLegacyReference);

    void delete(String objectKeyOrLegacyReference);

    String resolvePublicUrl(String objectKeyOrLegacyReference);

    /**
     * Normalizes a stored reference (object key, legacy {@code /uploads/...} path, or full URL)
     * to a canonical object key for persistence.
     */
    String normalizeToObjectKey(String storedReference);
}
