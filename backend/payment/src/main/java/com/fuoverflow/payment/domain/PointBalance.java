package com.fuoverflow.payment.domain;

import java.time.Instant;
import java.util.UUID;

public record PointBalance(UUID id, UUID userId, long balancePoints, Instant createdAt, Instant updatedAt) {
    public static PointBalance create(UUID userId) {
        return new PointBalance(UUID.randomUUID(), userId, 0L, Instant.now(), Instant.now());
    }
}
