package com.fuoverflow.common.storage;

import com.fuoverflow.common.config.FileStorageProperties;
import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

@Service
public class LocalFileStorageService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/png", "image/jpeg", "image/webp", "image/gif");
    private static final long MAX_BYTES = 5L * 1024 * 1024;
    private static final String UPLOADS_PREFIX = "/uploads/";

    private final FileStorageProperties properties;

    public LocalFileStorageService(FileStorageProperties properties) {
        this.properties = properties;
    }

    public String storeImage(MultipartFile file, String logicalFolder) {
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

        String extension = extensionFor(contentType);
        LocalDate today = LocalDate.now();
        String relativePath = logicalFolder + "/"
                + today.getYear() + "/"
                + String.format("%02d", today.getMonthValue()) + "/"
                + String.format("%02d", today.getDayOfMonth()) + "/"
                + UUID.randomUUID() + extension;

        Path target = resolvePhysicalPath(relativePath);
        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new BadRequestException("FILE_STORE_FAILED", "Failed to store uploaded file");
        }
        return UPLOADS_PREFIX + relativePath;
    }

    public void deleteIfManaged(String relativeUrl) {
        if (relativeUrl == null || relativeUrl.isBlank() || !relativeUrl.startsWith(UPLOADS_PREFIX)) {
            return;
        }
        Path physical = resolvePhysicalPath(relativeUrl.substring(UPLOADS_PREFIX.length()));
        try {
            Files.deleteIfExists(physical);
        } catch (IOException ignored) {
            // Best-effort cleanup; orphaned files can be swept later.
        }
    }

    private Path resolvePhysicalPath(String relativePath) {
        String uploadsPath = properties.uploadsPath();
        if (uploadsPath == null || uploadsPath.isBlank()) {
            throw new BadRequestException("STORAGE_NOT_CONFIGURED", "Upload storage path is not configured");
        }
        return Path.of(uploadsPath).resolve(relativePath).normalize();
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}
