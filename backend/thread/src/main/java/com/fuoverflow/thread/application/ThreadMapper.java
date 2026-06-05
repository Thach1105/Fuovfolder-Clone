package com.fuoverflow.thread.application;

import com.fuoverflow.thread.api.dto.ThreadDetailResponse;
import com.fuoverflow.thread.api.dto.ThreadSummaryResponse;
import com.fuoverflow.thread.persistence.ThreadEntity;

public final class ThreadMapper {
    private ThreadMapper() {
    }

    public static ThreadSummaryResponse toSummary(ThreadEntity t) {
        return new ThreadSummaryResponse(
                t.getId(),
                t.getForumId(),
                t.getCategoryId(),
                t.getTitle(),
                t.getSlug(),
                t.getStatus(),
                t.getImportedAuthorHandle(),
                t.getSourceUrl(),
                t.getReplyCount(),
                t.getViewCount(),
                t.getLastPostAt(),
                t.getCreatedAt());
    }

    public static ThreadDetailResponse toDetail(ThreadEntity t) {
        return new ThreadDetailResponse(
                t.getId(),
                t.getForumId(),
                t.getCategoryId(),
                t.getTitle(),
                t.getSlug(),
                t.getStatus(),
                t.getImportedAuthorHandle(),
                t.getSourceUrl(),
                t.getReplyCount(),
                t.getViewCount(),
                t.getReactionCount(),
                t.getLastPostAt(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
