package com.fuoverflow.app.external.api.dto;

import java.time.Instant;
import java.util.UUID;

public record PublicApiPayloadResponse(
        UUID id,
        Instant createdAt
) {
}
