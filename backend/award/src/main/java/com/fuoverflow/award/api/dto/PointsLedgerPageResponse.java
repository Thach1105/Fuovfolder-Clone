package com.fuoverflow.award.api.dto;

import java.util.List;

public record PointsLedgerPageResponse(
        List<PointsLedgerEntryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
