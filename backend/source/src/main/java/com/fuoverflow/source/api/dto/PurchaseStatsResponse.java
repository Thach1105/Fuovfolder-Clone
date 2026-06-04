package com.fuoverflow.source.api.dto;

public record PurchaseStatsResponse(
        long total,
        long active,
        long expired,
        long refunded
) {
}
