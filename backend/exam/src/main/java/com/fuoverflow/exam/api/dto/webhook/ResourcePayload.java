package com.fuoverflow.exam.api.dto.webhook;

/** A PE resource file the server must download from {@code sourceUrl}. */
public record ResourcePayload(
        Integer sortOrder,
        String folderLabel,
        String filename,
        String mimeType,
        Long sizeBytes,
        String sha256,
        String sourceUrl,
        String contentBase64
) {
    public ResourcePayload(Integer sortOrder, String folderLabel, String filename, String mimeType,
            Long sizeBytes, String sha256, String sourceUrl) {
        this(sortOrder, folderLabel, filename, mimeType, sizeBytes, sha256, sourceUrl, null);
    }
}
