package com.fuoverflow.exam.application;

import com.fuoverflow.common.storage.FileContentValidator;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.material.domain.UploadPurpose;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Writes webhook-ingested bytes into object storage.
 *
 * <p>Deliberately bypasses {@code UploadService}: that path needs a {@code MultipartFile}, an
 * owning user holding {@code exam.media.admin:create}, and it charges the per-user upload rate
 * limiter — a fifty-image paper would be rejected halfway through. Skipping the
 * {@code uploaded_files} bookkeeping is safe because {@code UploadService.deleteByStorageReference}
 * removes the object whether or not a row exists, and {@code markLinkedByStoragePath} is a no-op
 * when it does not, so the existing cleanup helpers still work on these keys.
 */
@Component
public class ExamIngestStorage {
    private static final Logger log = LoggerFactory.getLogger(ExamIngestStorage.class);
    private static final String DEFAULT_RESOURCE_TYPE = "application/octet-stream";

    private final ObjectStorage objectStorage;
    private final BlurImageGenerator blurGenerator;

    ExamIngestStorage(ObjectStorage objectStorage, BlurImageGenerator blurGenerator) {
        this.objectStorage = objectStorage;
        this.blurGenerator = blurGenerator;
    }

    public StoredImage storeImage(byte[] content, String mimeType, UploadPurpose purpose, boolean withBlur) {
        String objectKey = buildObjectKey(purpose.folder(), mimeType, "image");
        objectStorage.storeBytes(content, objectKey, mimeType);

        if (!withBlur) {
            return new StoredImage(objectKey, null);
        }
        String blurKey = ExamMediaService.deriveBlurKey(objectKey);
        try {
            byte[] blurBytes = blurGenerator.generateBlur(new ByteArrayInputStream(content));
            objectStorage.storeBytes(blurBytes, blurKey, "image/jpeg");
            return new StoredImage(objectKey, blurKey);
        } catch (Exception e) {
            // A missing blur degrades the paywall for that one image; losing the original would
            // lose content, so the original stays and the sidecar is simply absent.
            log.warn("Failed to generate blur for {}: {}", objectKey, e.getMessage());
            return new StoredImage(objectKey, null);
        }
    }

    public String storeResource(byte[] content, String mimeType, String filename) {
        String contentType = (mimeType == null || mimeType.isBlank()) ? DEFAULT_RESOURCE_TYPE : mimeType;
        String objectKey = buildObjectKey(
                UploadPurpose.EXAM_PE_RESOURCE.folder(), contentType, filename);
        objectStorage.storeBytes(content, objectKey, contentType);
        return objectKey;
    }

    /**
     * Same layout {@code ObjectStorageSupport.buildObjectKey} produces — duplicated because that
     * helper is package-private in {@code common} — so ingested objects sit alongside uploaded
     * ones and {@code normalizeToObjectKey}/{@code resolvePublicUrl} treat them identically.
     */
    private static String buildObjectKey(String logicalFolder, String contentType, String filename) {
        LocalDate today = LocalDate.now();
        String safeName = FileContentValidator.sanitizeFilename(filename);
        String extension = FileContentValidator.extensionFor(contentType, safeName);
        return logicalFolder + "/"
                + today.getYear() + "/"
                + String.format("%02d", today.getMonthValue()) + "/"
                + String.format("%02d", today.getDayOfMonth()) + "/"
                + UUID.randomUUID() + extension;
    }

    public record StoredImage(String objectKey, String blurObjectKey) {
    }
}
