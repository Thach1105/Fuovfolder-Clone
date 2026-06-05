package com.fuoverflow.source.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CatalogItemDetailResponse(
        UUID id,
        String code,
        String title,
        String description,
        int pricePoints,
        int accessDays,
        int questionCount,
        double duplicationRatePercent,
        double passRatePercent,
        long viewCount,
        String cardColor,
        String categorySlug,
        boolean featured,
        List<CatalogItemResponse> related,
        boolean hasActiveAccess,
        Instant activeAccessEndsAt
) {
}
