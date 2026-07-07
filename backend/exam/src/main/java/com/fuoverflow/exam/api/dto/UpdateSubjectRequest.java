package com.fuoverflow.exam.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateSubjectRequest(
        @Size(max = 64) String code,
        @Size(max = 500) String title,
        String description,
        @Size(max = 500) String coverImageUrl,
        @Size(max = 32) String cardColor,
        @Size(max = 64) String categorySlug,
        @Min(0) Integer fePreviewCount,
        Boolean active,
        Integer sortOrder
) {
}
