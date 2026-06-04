package com.fuoverflow.source.api.dto;

import java.util.UUID;

public record CatalogItemResponse(
        UUID id,
        String code,
        String title,
        int pricePoints,
        int accessDays,
        int questionCount,
        double duplicationRatePercent,
        double passRatePercent,
        long viewCount,
        String cardColor,
        boolean featured
) {
}
