package com.fuoverflow.source.api.dto;

import java.util.List;

public record CheckScoreSubjectsResponse(
        List<String> items,
        int total,
        int page,
        int limit,
        boolean hasPrevious,
        boolean hasNext,
        int totalPages
) {
}
