package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "fuexam.upload")
public record UploadProperties(
        long imageMaxBytes,
        long documentMaxBytes,
        int maxUploadsPerHour,
        List<String> allowedImageTypes,
        List<String> allowedDocumentTypes
) {
    public UploadProperties {
        if (imageMaxBytes <= 0) {
            imageMaxBytes = 5L * 1024 * 1024;
        }
        if (documentMaxBytes <= 0) {
            documentMaxBytes = 20L * 1024 * 1024;
        }
        if (maxUploadsPerHour <= 0) {
            maxUploadsPerHour = 30;
        }
        if (allowedImageTypes == null || allowedImageTypes.isEmpty()) {
            allowedImageTypes = List.of("image/png", "image/jpeg", "image/webp", "image/gif");
        }
        if (allowedDocumentTypes == null || allowedDocumentTypes.isEmpty()) {
            allowedDocumentTypes = List.of(
                    "application/pdf",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation");
        }
    }
}
