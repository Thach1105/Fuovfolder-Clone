package com.fuoverflow.broadcast.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record BroadcastConfigResponse(
        UUID id,
        String eventType,
        Map<String, Object> config,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {}
