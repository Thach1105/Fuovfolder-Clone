package com.fuoverflow.forum.api.dto;

import com.fuoverflow.forum.persistence.ForumEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ForumResponse(
        UUID id,
        String slug,
        String title,
        String description,
        String visibility,
        Integer sortOrder,
        UUID createdByUserId,
        long threadCount,
        Instant createdAt,
        Instant updatedAt,
        List<CategoryResponse> categories
) {
    public static ForumResponse fromEntity(ForumEntity entity, long threadCount, List<CategoryResponse> categories) {
        return new ForumResponse(
                entity.getId(),
                entity.getSlug(),
                entity.getTitle(),
                entity.getDescription(),
                entity.getVisibility().name(),
                entity.getSortOrder(),
                entity.getCreatedByUserId(),
                threadCount,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                categories
        );
    }
}
