package com.fuoverflow.post.api.dto;

import java.util.List;

public record PostPageResponse(
        List<PostResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
