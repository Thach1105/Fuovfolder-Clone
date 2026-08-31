package com.fuoverflow.exam.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Everything an admin needs to look at one paper before publishing it.
 *
 * <p>One shape serves both kinds: an FE paper fills {@code questions}, a PE paper fills
 * {@code images} and {@code resources}. Image references are plain object keys, matching the other
 * admin endpoints, so the console resolves them with its own {@code examMediaUrl()} helper.
 */
public record AdminPaperContentResponse(
        AdminPaperResponse paper,
        List<AdminPaperQuestion> questions,
        List<String> images,
        List<AdminPeItemResponse.AdminPeResourceResponse> resources
) {
    public record AdminPaperQuestion(
            UUID id,
            String questionText,
            List<String> imageUrls,
            List<String> blurUrls,
            int sortOrder
    ) {
    }
}
