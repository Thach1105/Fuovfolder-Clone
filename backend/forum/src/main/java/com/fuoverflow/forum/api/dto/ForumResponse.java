package com.fuoverflow.forum.api.dto;

import java.time.Instant;
import java.util.UUID;

public record ForumResponse(
        UUID id,
        String slug,
        String title,
        String description,
        String visibility,
        Instant createdAt
) {
}
