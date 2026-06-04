package com.fuoverflow.coursera.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCatalogItemResponse(
        UUID id,
        String code,
        String title,
        String description,
        int pricePoints,
        boolean active,
        boolean featured,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
}
