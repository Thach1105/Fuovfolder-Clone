package com.fuoverflow.forum.api.dto;

import java.time.Instant;
import java.util.UUID;

public record SyncRunResponse(
        UUID id,
        String mode,
        String scope,
        String status,
        int forumsSynced,
        int threadsSynced,
        int postsSynced,
        String errorMessage,
        Instant startedAt,
        Instant finishedAt
) {
}
