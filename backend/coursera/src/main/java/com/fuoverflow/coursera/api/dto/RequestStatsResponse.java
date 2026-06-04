package com.fuoverflow.coursera.api.dto;

public record RequestStatsResponse(
        long total,
        long pending,
        long inProgress,
        long completed,
        long cancelled
) {
}
