package com.fuoverflow.source.api.dto;

public record SourceOverviewResponse(
        long totalPurchases,
        long activePurchases,
        long refundedPurchases,
        long activeCatalogItems,
        long paidPoints,
        long refundedPoints
) {
}
