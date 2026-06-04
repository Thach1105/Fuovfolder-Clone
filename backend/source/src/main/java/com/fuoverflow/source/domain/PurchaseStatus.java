package com.fuoverflow.source.domain;

import com.fuoverflow.common.exception.BadRequestException;

public enum PurchaseStatus {
    ACTIVE,
    EXPIRED,
    REFUNDED,
    CANCELLED;

    public String toDb() {
        return name().toLowerCase();
    }

    public static PurchaseStatus fromDb(String value) {
        return switch (value) {
            case "active" -> ACTIVE;
            case "expired" -> EXPIRED;
            case "refunded" -> REFUNDED;
            case "cancelled" -> CANCELLED;
            default -> throw new BadRequestException("INVALID_STATUS", "Unknown purchase status: " + value);
        };
    }
}
