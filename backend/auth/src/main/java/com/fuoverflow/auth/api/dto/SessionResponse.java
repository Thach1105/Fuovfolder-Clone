package com.fuoverflow.auth.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
        UUID id,
        String ipAddress,
        String userAgent,
        String deviceLabel,
        Instant issuedAt,
        Instant lastUsedAt,
        boolean current
) {}
