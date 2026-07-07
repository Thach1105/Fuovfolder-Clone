package com.fuoverflow.exam.api.dto;

import java.util.List;
import java.util.UUID;

public record PublicPeItemResponse(
        UUID id,
        String title,
        String description,
        List<String> examImageUrls,
        int sortOrder,
        List<PublicPeResourceResponse> resources
) {
    public record PublicPeResourceResponse(
            UUID id,
            String folderLabel,
            String originalFilename,
            String mimeType,
            long sizeBytes,
            int sortOrder,
            String downloadUrl
    ) {
    }
}
