package com.fuoverflow.exam.domain;

/** A PE resource file to be downloaded from {@code sourceUrl} and verified against {@code sha256}. */
public record IngestResource(
        int sortOrder,
        String folderLabel,
        String filename,
        String mimeType,
        long sizeBytes,
        String sha256,
        String sourceUrl,
        byte[] content
) {
    public IngestResource(int sortOrder, String folderLabel, String filename, String mimeType,
            long sizeBytes, String sha256, String sourceUrl) {
        this(sortOrder, folderLabel, filename, mimeType, sizeBytes, sha256, sourceUrl, null);
    }
}
