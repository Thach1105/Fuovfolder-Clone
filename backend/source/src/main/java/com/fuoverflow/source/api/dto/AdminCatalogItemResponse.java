package com.fuoverflow.source.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCatalogItemResponse(
        UUID id,
        String code,
        String title,
        String description,
        int pricePoints,
        int accessDays,
        int questionCount,
        int duplicationRateBp,
        int passRateBp,
        long viewCount,
        String cardColor,
        String coverImageUrl,
        String categorySlug,
        boolean active,
        boolean featured,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
