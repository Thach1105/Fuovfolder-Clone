package com.fuoverflow.coursera.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminRequestDetailResponse(
        UUID id,
        UUID userId,
        String username,
        String displayName,
        String status,
        int totalPoints,
        String userNotes,
        String courseraEmail,
        String courseraPassword,
        List<RequestDetailResponse.RequestItemLineResponse> items,
        Instant createdAt,
        Instant statusChangedAt,
        UUID assignedToUserId,
        List<String> allowedNextStatuses,
        boolean refunded,
        UUID paymentLedgerId,
        UUID refundLedgerId
) {
}
