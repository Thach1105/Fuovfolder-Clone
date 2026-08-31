package com.fuoverflow.exam.api.dto.webhook;

/** A PE resource file the server must download from {@code sourceUrl}. */
public record ResourcePayload(
        Integer sortOrder,
        String folderLabel,
        String filename,
        String mimeType,
        Long sizeBytes,
        String sha256,
        String sourceUrl
) {
}
