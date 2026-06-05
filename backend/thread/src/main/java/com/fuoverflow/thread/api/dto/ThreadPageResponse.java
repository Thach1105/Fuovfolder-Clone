package com.fuoverflow.thread.api.dto;

import java.util.List;

public record ThreadPageResponse(
        List<ThreadSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
