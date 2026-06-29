package com.fuoverflow.common.broadcast;

import java.util.Map;
import java.util.UUID;

public record DepositCompletedEvent(
        UUID userId,
        String displayName,
        long amountVnd,
        long pointsEarned
) {
    public Map<String, Object> toEventData() {
        return Map.of(
                "userId", userId.toString(),
                "displayName", displayName,
                "amountVnd", amountVnd,
                "pointsEarned", pointsEarned
        );
    }
}
