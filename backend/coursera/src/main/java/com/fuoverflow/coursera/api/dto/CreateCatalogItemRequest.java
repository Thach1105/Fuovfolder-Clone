package com.fuoverflow.coursera.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateCatalogItemRequest(
        @NotBlank @Size(max = 64) String code,
        @NotBlank @Size(max = 500) String title,
        String description,
        @NotNull @Min(0) Integer pricePoints,
        Boolean active,
        Boolean featured,
        Integer sortOrder
) {
}
