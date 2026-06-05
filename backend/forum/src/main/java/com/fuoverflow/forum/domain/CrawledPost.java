package com.fuoverflow.forum.domain;

import java.time.Instant;

/**
 * A single post parsed from a XenForo thread page.
 */
public record CrawledPost(
        String externalId,
        String authorHandle,
        String authorUserId,
        String bodyHtml,
        String bodyText,
        Instant postedAt,
        String sourceUrl
) {
}
