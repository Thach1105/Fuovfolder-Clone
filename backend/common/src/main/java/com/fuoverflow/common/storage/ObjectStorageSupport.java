package com.fuoverflow.common.storage;

import com.fuoverflow.common.config.UploadProperties;
import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

final class ObjectStorageSupport {
    static final String LEGACY_UPLOADS_PREFIX = "/uploads/";

    private ObjectStorageSupport() {
    }

    static void validateImage(MultipartFile file, UploadProperties properties) {
        FileContentValidator.validate(file, FileKind.IMAGE, properties);
    }

    static void validateFile(MultipartFile file, FileKind kind, UploadProperties properties) {
        FileContentValidator.validate(file, kind, properties);
    }

    static String buildObjectKey(String logicalFolder, String contentType, String originalFilename) {
        LocalDate today = LocalDate.now();
        String safeName = FileContentValidator.sanitizeFilename(originalFilename);
        String extension = FileContentValidator.extensionFor(contentType, safeName);
        return logicalFolder + "/"
                + today.getYear() + "/"
                + String.format("%02d", today.getMonthValue()) + "/"
                + String.format("%02d", today.getDayOfMonth()) + "/"
                + UUID.randomUUID() + extension;
    }

    static String buildObjectKey(String logicalFolder, String contentType) {
        return buildObjectKey(logicalFolder, contentType, "upload.bin");
    }

    static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    static String requireContentType(MultipartFile file) {
        String contentType = file.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw new BadRequestException("FILE_TYPE_INVALID", "Missing content type");
        }
        return contentType.toLowerCase().split(";")[0].trim();
    }
}
