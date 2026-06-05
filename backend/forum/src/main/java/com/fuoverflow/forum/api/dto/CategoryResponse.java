package com.fuoverflow.forum.api.dto;

import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID forumId,
        String slug,
        String title,
        String description,
        String visibility
) {
}
