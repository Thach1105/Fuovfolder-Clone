package com.fuoverflow.source.api.dto;

import java.util.List;

public record PurchasePageResponse(
        List<PurchaseResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
