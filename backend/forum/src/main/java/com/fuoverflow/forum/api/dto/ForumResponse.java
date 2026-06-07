package com.fuoverflow.forum.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForumResponse(
        UUID id,
        String slug,
        String title,
        String description,
        String visibility,
        Instant createdAt,
        UUID parentForumId,
        List<ForumResponse> children
) {
    public ForumResponse(
            UUID id,
            String slug,
            String title,
            String description,
            String visibility,
            Instant createdAt) {
        this(id, slug, title, description, visibility, createdAt, null, List.of());
    }
}
