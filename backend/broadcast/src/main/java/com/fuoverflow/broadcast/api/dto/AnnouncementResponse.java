package com.fuoverflow.broadcast.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AnnouncementResponse(
        UUID id,
        String title,
        String contentHtml,
        String backgroundColor,
        String linkUrl,
        String linkLabel,
        int priority,
        int scrollSpeed,
        int stepSeconds,
        String status,
        Instant startAt,
        Instant endAt,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {}
