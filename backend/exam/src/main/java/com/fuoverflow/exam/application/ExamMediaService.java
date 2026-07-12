package com.fuoverflow.exam.application;

import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.common.storage.StoredObject;
import com.fuoverflow.material.api.dto.UploadResponse;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.material.domain.UploadPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

@Service
public class ExamMediaService {
    private static final Logger log = LoggerFactory.getLogger(ExamMediaService.class);

    private final UploadService uploadService;
    private final ObjectStorage objectStorage;
    private final BlurImageGenerator blurGenerator;

    public ExamMediaService(UploadService uploadService, ObjectStorage objectStorage,
                            BlurImageGenerator blurGenerator) {
        this.uploadService = uploadService;
        this.objectStorage = objectStorage;
        this.blurGenerator = blurGenerator;
    }

    public StoredObject upload(MultipartFile file, UploadPurpose purpose, UUID adminUserId) {
        UploadResponse uploaded = uploadService.upload(file, purpose, adminUserId,
                SecurityContextHolder.getContext().getAuthentication());
        return new StoredObject(uploaded.objectKey(), uploaded.publicUrl());
    }

    public BlurUploadResult uploadWithBlur(MultipartFile file, UploadPurpose purpose, UUID adminUserId) {
        StoredObject stored = upload(file, purpose, adminUserId);
        String blurKey = deriveBlurKey(stored.objectKey());
        try (InputStream stream = objectStorage.openStream(stored.objectKey())) {
            byte[] blurBytes = blurGenerator.generateBlur(stream);
            objectStorage.storeBytes(blurBytes, blurKey, "image/jpeg");
        } catch (Exception e) {
            log.warn("Failed to generate blur for {}: {}", stored.objectKey(), e.getMessage());
        }
        return new BlurUploadResult(stored.objectKey(), blurKey, stored.publicUrl());
    }

    public void markLinked(String objectKey) {
        uploadService.markLinkedByStoragePath(objectKey);
    }

    public void markLinkedAll(List<String> objectKeys) {
        if (objectKeys == null) return;
        for (String key : objectKeys) {
            markLinked(key);
        }
    }

    public void unlinkStoredReference(String objectKeyOrLegacyReference) {
        uploadService.markUnlinkedByObjectKey(objectKeyOrLegacyReference);
    }

    public void unlinkStoredReferences(List<String> references) {
        if (references == null) return;
        for (String ref : references) {
            unlinkStoredReference(ref);
        }
    }

    public void deleteStoredReference(String objectKeyOrLegacyReference) {
        uploadService.deleteByStorageReference(objectKeyOrLegacyReference);
    }

    public void deleteStoredReferences(List<String> references) {
        if (references == null) return;
        for (String ref : references) {
            deleteStoredReference(ref);
        }
    }

    public void deletePaired(String objectKey, String blurKey) {
        deleteStoredReference(objectKey);
        if (blurKey != null) {
            try {
                objectStorage.delete(blurKey);
            } catch (Exception e) {
                log.warn("Failed to delete blur file {}: {}", blurKey, e.getMessage());
            }
        }
    }

    public void deletePairedAll(List<String> objectKeys, List<String> blurKeys) {
        deleteStoredReferences(objectKeys);
        if (blurKeys == null) return;
        for (String blurKey : blurKeys) {
            if (blurKey != null) {
                try {
                    objectStorage.delete(blurKey);
                } catch (Exception e) {
                    log.warn("Failed to delete blur file {}: {}", blurKey, e.getMessage());
                }
            }
        }
    }

    static String deriveBlurKey(String objectKey) {
        int lastDot = objectKey.lastIndexOf('.');
        if (lastDot > 0) {
            return objectKey.substring(0, lastDot) + "-blur.jpg";
        }
        return objectKey + "-blur.jpg";
    }

    public record BlurUploadResult(String objectKey, String blurObjectKey, String publicUrl) {}
}
