package com.fuoverflow.deposit.api.dto;

import com.fuoverflow.deposit.persistence.DepositTierEntity;

import java.time.Instant;
import java.util.UUID;

public record AdminDepositTierResponse(
        UUID id,
        String label,
        int amountVnd,
        int points,
        int bonusPercent,
        long totalPoints,
        boolean active,
        int sortOrder,
        Instant createdAt,
        Instant updatedAt
) {
    public static AdminDepositTierResponse from(DepositTierEntity e) {
        return new AdminDepositTierResponse(
                e.getId(),
                e.getLabel(),
                e.getAmountVnd(),
                e.getPoints(),
                e.getBonusPercent(),
                e.totalPoints(),
                e.isActive(),
                e.getSortOrder(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }
}
