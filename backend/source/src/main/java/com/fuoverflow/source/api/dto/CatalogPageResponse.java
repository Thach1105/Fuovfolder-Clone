package com.fuoverflow.source.api.dto;

import java.util.List;

public record CatalogPageResponse(
        List<CatalogItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
