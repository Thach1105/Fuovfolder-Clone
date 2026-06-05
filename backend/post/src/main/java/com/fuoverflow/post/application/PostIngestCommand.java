package com.fuoverflow.post.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Normalized post payload handed from the forum crawl pipeline to the post module.
 * {@code localId} is resolved by the caller via the external mapping for idempotency.
 */
public record PostIngestCommand(
        UUID localId,
        UUID threadId,
        UUID authorUserId,
        String bodyMd,
        String bodyHtml,
        String importedAuthorHandle,
        String sourceUrl,
        Instant createdAt
) {
}
