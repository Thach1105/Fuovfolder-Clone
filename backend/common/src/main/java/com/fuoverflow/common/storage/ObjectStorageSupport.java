package com.fuoverflow.common.storage;

import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

final class ObjectStorageSupport {
    static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");
    static final long MAX_BYTES = 5L * 1024 * 1024;
    static final String LEGACY_UPLOADS_PREFIX = "/uploads/";

    private ObjectStorageSupport() {
    }

    static void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("FILE_EMPTY", "Uploaded file is empty");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BadRequestException("FILE_TYPE_INVALID", "Only PNG, JPEG, WebP, and GIF images are allowed");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new BadRequestException("FILE_TOO_LARGE", "Image must be 5 MB or smaller");
        }
    }

    static String buildObjectKey(String logicalFolder, String contentType) {
        LocalDate today = LocalDate.now();
        return logicalFolder + "/"
                + today.getYear() + "/"
                + String.format("%02d", today.getMonthValue()) + "/"
                + String.format("%02d", today.getDayOfMonth()) + "/"
                + UUID.randomUUID() + extensionFor(contentType);
    }

    static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }

    static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
