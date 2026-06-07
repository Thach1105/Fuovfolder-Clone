package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCatalogItemRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 500) String title,
        String description,
        @NotNull @Min(0) Integer pricePoints,
        @Min(1) Integer accessDays,
        @Min(0) Integer questionCount,
        @Min(0) Integer duplicationRateBp,
        @Min(0) Integer passRateBp,
        @Size(max = 32) String cardColor,
        @Size(max = 500) String coverImageUrl,
        @Size(max = 64) String categorySlug,
        Boolean active,
        Boolean featured,
        Integer sortOrder
) {
}
