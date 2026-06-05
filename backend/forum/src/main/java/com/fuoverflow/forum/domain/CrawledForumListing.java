package com.fuoverflow.forum.domain;

import java.util.List;

/**
 * The parsed result of one forum (node) listing page: forum identity, the thread
 * rows on this page, and the highest page number available for pagination.
 */
public record CrawledForumListing(
        String forumSlug,
        String forumTitle,
        List<CrawledThreadSummary> threads,
        int currentPage,
        int lastPage
) {
}
