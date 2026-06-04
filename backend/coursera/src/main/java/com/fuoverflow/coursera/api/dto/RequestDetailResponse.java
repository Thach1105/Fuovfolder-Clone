package com.fuoverflow.coursera.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record RequestDetailResponse(
        UUID id,
        String status,
        int totalPoints,
        String userNotes,
        String courseraEmail,
        List<RequestItemLineResponse> items,
        Instant createdAt,
        Instant statusChangedAt
) {
    public record RequestItemLineResponse(
            UUID catalogItemId,
            String title,
            int unitPricePoints,
            int quantity
    ) {
    }
}
