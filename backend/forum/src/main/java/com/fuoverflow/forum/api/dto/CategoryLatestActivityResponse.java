package com.fuoverflow.forum.api.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryLatestActivityResponse(
        UUID threadId,
        String threadTitle,
        String threadType,
        String authorHandle,
        Instant postedAt
) {
}
