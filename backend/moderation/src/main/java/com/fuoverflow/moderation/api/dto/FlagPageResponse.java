package com.fuoverflow.moderation.api.dto;

import com.fuoverflow.post.api.dto.PostResponse;

import java.util.List;

public record FlagPageResponse(
        List<FlagResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
