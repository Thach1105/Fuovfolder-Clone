package com.fuoverflow.exam.application;

import com.fuoverflow.common.storage.ObjectStorage;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ExamMediaUrlResolver {
    private final ObjectStorage objectStorage;
    private final ExamMediaTokenService tokenService;

    public ExamMediaUrlResolver(ObjectStorage objectStorage, ExamMediaTokenService tokenService) {
        this.objectStorage = objectStorage;
        this.tokenService = tokenService;
    }

    /** Normalize any stored reference (object key, legacy path, full URL) to a canonical object key. */
    public String normalizeForStorage(String imageReference) {
        if (imageReference == null || imageReference.isBlank()) {
            return null;
        }
        return objectStorage.normalizeToObjectKey(imageReference);
    }

    /** Short-lived HMAC-signed URL for members/preview consumers ({@code <img>} src). */
    public String signed(String objectKey) {
        return tokenService.generateSignedUrl(objectKey);
    }

    public List<String> signedAll(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }
        return objectKeys.stream().map(tokenService::generateSignedUrl).toList();
    }

    /** Plain public/legacy URL for admin previews (no signing). */
    public String plain(String objectKey) {
        return objectStorage.resolvePublicUrl(objectKey);
    }

    public List<String> plainAll(List<String> objectKeys) {
        if (objectKeys == null || objectKeys.isEmpty()) {
            return List.of();
        }
        return objectKeys.stream().map(objectStorage::resolvePublicUrl).toList();
    }
}
