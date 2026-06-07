package com.fuoverflow.material.api.dto;

import java.util.UUID;

public record AttachmentResponse(
        UUID fileId,
        String originalFilename,
        String mimeType,
        long sizeBytes
) {
}
