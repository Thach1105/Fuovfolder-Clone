package com.fuoverflow.exam.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddPeResourceRequest(
        @NotBlank @Size(max = 500) String objectKey,
        @NotBlank @Size(max = 500) String originalFilename,
        @Size(max = 120) String mimeType,
        Long sizeBytes,
        @Size(max = 255) String folderLabel,
        Integer sortOrder
) {
}
