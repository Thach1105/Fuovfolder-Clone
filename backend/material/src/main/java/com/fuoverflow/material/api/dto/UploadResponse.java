package com.fuoverflow.material.api.dto;

import java.util.UUID;

public record UploadResponse(
        UUID fileId,
        String objectKey,
        String publicUrl,
        String mimeType,
        long sizeBytes,
        String originalFilename
) {
}
