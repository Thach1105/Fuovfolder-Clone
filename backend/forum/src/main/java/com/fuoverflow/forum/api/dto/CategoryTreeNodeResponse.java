package com.fuoverflow.forum.api.dto;

import java.util.List;
import java.util.UUID;

public record CategoryTreeNodeResponse(
        UUID id,
        UUID forumId,
        UUID parentId,
        String slug,
        String title,
        String description,
        String visibility,
        String iconColor,
        int sortOrder,
        int topicCount,
        int postCount,
        CategoryLatestActivityResponse latestActivity,
        List<CategoryTreeNodeResponse> children
) {
}
