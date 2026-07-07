package com.fuoverflow.exam.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminPeItemResponse(
        UUID id,
        UUID subjectId,
        String title,
        String description,
        List<String> examImageUrls,
        int sortOrder,
        List<AdminPeResourceResponse> resources,
        Instant createdAt,
        Instant updatedAt
) {
    public record AdminPeResourceResponse(
            UUID id,
            String folderLabel,
            String objectKey,
            String originalFilename,
            String mimeType,
            long sizeBytes,
            int sortOrder
    ) {
    }
}
