package com.fuoverflow.post.application;

import com.fuoverflow.post.api.dto.PostResponse;
import com.fuoverflow.post.persistence.PostEntity;

final class PostMapper {
    private PostMapper() {
    }

    static PostResponse toResponse(PostEntity post) {
        return new PostResponse(
                post.getId(),
                post.getThreadId(),
                post.getAuthorUserId(),
                post.getParentPostId(),
                post.getImportedAuthorHandle(),
                post.getBodyMd(),
                post.getBodyHtml(),
                post.getStatus(),
                post.getEditVersion(),
                post.getReactionCount(),
                post.getSourceUrl(),
                post.getCreatedAt(),
                post.getLastEditedAt());
    }
}
