package com.fuoverflow.coursera.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateCatalogItemRequest(
        @Size(max = 64) String code,
        @Size(max = 500) String title,
        String description,
        @Min(0) Integer pricePoints,
        Boolean active,
        Boolean featured,
        Integer sortOrder
) {
}
