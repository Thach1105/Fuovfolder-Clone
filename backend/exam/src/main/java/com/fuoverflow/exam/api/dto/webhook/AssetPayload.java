package com.fuoverflow.exam.api.dto.webhook;

/**
 * An inline image. {@code sha256} and {@code sizeBytes} are transport hints only: the validator
 * recomputes both from the decoded bytes, and {@code mimeType} is re-derived from the magic bytes.
 */
public record AssetPayload(
        Integer sortOrder,
        String mimeType,
        Long sizeBytes,
        String sha256,
        String contentBase64
) {
}
