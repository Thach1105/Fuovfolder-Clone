package com.fuoverflow.source.api.dto;

import java.util.List;

public record AdminPurchasePageResponse(
        List<AdminPurchaseResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
