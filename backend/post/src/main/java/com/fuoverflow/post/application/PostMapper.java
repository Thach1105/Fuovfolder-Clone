package com.fuoverflow.post.application;

import com.fuoverflow.material.api.dto.AttachmentResponse;
import com.fuoverflow.post.api.dto.PostResponse;
import com.fuoverflow.post.persistence.PostEntity;

import java.util.List;

final class PostMapper {
    private PostMapper() {
    }

    static PostResponse toResponse(PostEntity post, String authorAvatarUrl, List<AttachmentResponse> attachments) {
        return new PostResponse(
                post.getId(),
                post.getThreadId(),
                post.getAuthorUserId(),
                post.getParentPostId(),
                post.getImportedAuthorHandle(),
                authorAvatarUrl,
                post.getBodyMd(),
                post.getBodyHtml(),
                post.getStatus(),
                post.getEditVersion(),
                post.getReactionCount(),
                post.getSourceUrl(),
                attachments == null ? List.of() : attachments,
                post.getCreatedAt(),
                post.getLastEditedAt());
    }
}
