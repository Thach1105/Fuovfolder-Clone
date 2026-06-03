package com.fuoverflow.forum.api.dto;

import com.fuoverflow.forum.persistence.CategoryEntity;
import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID forumId,
        String slug,
        String title,
        String description,
        Integer sortOrder,
        String visibility,
        long threadCount,
        long postCount,
        Instant createdAt,
        Instant updatedAt
) {
    public static CategoryResponse fromEntity(CategoryEntity entity, long threadCount, long postCount) {
        return new CategoryResponse(
                entity.getId(),
                entity.getForumId(),
                entity.getSlug(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getSortOrder(),
                entity.getVisibility().name(),
                threadCount,
                postCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
