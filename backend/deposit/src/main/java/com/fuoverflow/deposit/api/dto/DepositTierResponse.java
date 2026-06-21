package com.fuoverflow.deposit.api.dto;

import com.fuoverflow.deposit.persistence.DepositTierEntity;

import java.util.UUID;

public record DepositTierResponse(
        UUID id,
        String label,
        int amountVnd,
        long totalPoints,
        int bonusPercent,
        int sortOrder
) {
    public static DepositTierResponse from(DepositTierEntity e) {
        return new DepositTierResponse(
                e.getId(),
                e.getLabel(),
                e.getAmountVnd(),
                e.totalPoints(),
                e.getBonusPercent(),
                e.getSortOrder()
        );
    }
}
