package com.fuoverflow.forum.domain;

import java.time.Instant;

/**
 * A thread row parsed from a forum (node) listing page.
 */
public record CrawledThreadSummary(
        String externalId,
        String title,
        String slug,
        String path,
        String authorHandle,
        Instant startedAt,
        Instant lastPostAt,
        Integer replyCount,
        Long viewCount
) {
}
