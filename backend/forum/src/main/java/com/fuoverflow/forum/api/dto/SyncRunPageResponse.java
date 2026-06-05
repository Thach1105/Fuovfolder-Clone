package com.fuoverflow.forum.api.dto;

import java.util.List;

public record SyncRunPageResponse(
        List<SyncRunResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
