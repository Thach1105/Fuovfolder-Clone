package com.fuoverflow.forum.domain;

import java.util.List;

/**
 * The fully parsed content of a single thread (its metadata plus a page of posts).
 */
public record CrawledThreadPage(
        String externalId,
        String title,
        List<CrawledPost> posts,
        int currentPage,
        int lastPage
) {
}
