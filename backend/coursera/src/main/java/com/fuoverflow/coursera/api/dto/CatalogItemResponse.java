package com.fuoverflow.coursera.api.dto;

import java.util.UUID;

public record CatalogItemResponse(
        UUID id,
        String code,
        String title,
        String description,
        int pricePoints,
        boolean featured
) {
}
