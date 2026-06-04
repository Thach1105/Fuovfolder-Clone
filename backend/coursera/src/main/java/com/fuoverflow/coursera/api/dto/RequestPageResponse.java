package com.fuoverflow.coursera.api.dto;

import java.util.List;

public record RequestPageResponse(
        List<RequestSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
