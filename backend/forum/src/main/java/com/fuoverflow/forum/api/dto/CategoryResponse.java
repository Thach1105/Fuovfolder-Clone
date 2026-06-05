package com.fuoverflow.forum.api.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID forumId,
        UUID parentId,
        String slug,
        String title,
        String description,
        String visibility,
        String iconColor,
        int sortOrder
) {
}
