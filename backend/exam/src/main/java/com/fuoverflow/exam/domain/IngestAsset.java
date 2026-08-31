package com.fuoverflow.exam.domain;

/**
 * An image that arrived inline in a webhook payload, already base64-decoded and type-checked.
 * {@code mimeType} is what the magic bytes say, not what the caller declared.
 */
public record IngestAsset(int sortOrder, String mimeType, long sizeBytes, String sha256, byte[] content) {
}
