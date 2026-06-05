package com.fuoverflow.thread.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Normalized thread payload handed from the forum crawl pipeline to the thread
 * module. {@code localId} is resolved by the caller via the external mapping so the
 * upsert stays idempotent across runs.
 */
public record ThreadIngestCommand(
        UUID localId,
        UUID forumId,
        UUID categoryId,
        UUID authorUserId,
        String title,
        String slug,
        String importedAuthorHandle,
        String sourceUrl,
        Instant lastPostAt,
        Integer replyCount,
        Long viewCount
) {
}
